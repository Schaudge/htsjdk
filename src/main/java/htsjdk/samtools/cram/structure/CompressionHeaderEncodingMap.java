package htsjdk.samtools.cram.structure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import htsjdk.samtools.cram.compression.RANSExternalCompressor;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.encoding.external.ByteArrayStopEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalByteEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.ExternalLongEncoding;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Maintains a map of the EncodingDescriptor for each Data Series, and for each such descriptor
 * that represents an EXTERNAL encoding, the corresponding compressor to use.
 *
 * There are three constructors; one populates the map from scratch using the default encodings chosen by
 * this (htsjdk) implementation (when writing a new CRAM); one populates the map from a serialized
 * CRAM stream, resulting in encodings chosen by the implementation that wrote that CRAM; and one populates
 * the map from a CompressionHeaderEncodingMap that was previously serialized as part of a serialized
 * CRAMEncodingStrategy. The latter serialized format is different from the native CRAM serialized form because
 * in a CRAM stream, the compressors are not stored as part of the encoding amp, but rather are stored with the
 * block containing the data that uses that compression method.
 *
 * Although the CRAM spec defines a fixed list of data series, individual CRAM implementations
 * may choose to use only a subset of these. Therefore, the actual set of encodings that are
 * instantiated can vary depending on the source.
 *
 * Notes on the CRAM write implementation: This implementation encodes ALL DataSeries to external blocks,
 * (although some of the external encodings split the data between core and external; see
 * {@link htsjdk.samtools.cram.encoding.ByteArrayLenEncoding}, and does not use the 'BB' or 'QQ'
 * DataSeries when writing CRAM at all.
 *
 * See {@link htsjdk.samtools.cram.encoding.EncodingFactory} for details on how an {@link EncodingDescriptor}
 * is mapped to the codec that actually transfers data to and from underlying Slice blocks.
 */
public class CompressionHeaderEncodingMap {
    private static final long serialVersionUID = 1L;

    // Encoding descriptors for each data series. (These encodings can be either EXTERNAL or CORE, although
    // the spec does not make a clear distinction between EXTERNAL and CODE for encodings; only for blocks.
    // See https://github.com/samtools/hts-specs/issues/426).
    private Map<DataSeries, EncodingDescriptor> encodingMap = new TreeMap<>();

    // External compressor to use for each external block, keyed by external content ID. This
    // map contains a key for each data series that is in an external block, plus additional
    // ones for each external block used for tags.
    private final Map<Integer, ExternalCompressor> externalCompressors = new TreeMap<>();

    /**
     * Constructor used to create an encoding map for writing CRAMs
     */
    // TODO: pull the encoding map out of the encoding strategy if there is one ?
    // TODO: it seems backwards to require a CRAMEncodingStrategy to create a CompressionHeaderEncodingMap
    // TODO: when CRAMEncodingStrategy has a CompressionHeaderEncodingMap
    // TODO: though CRAMEncodingStrategy does have some params, like gzip level that are needed to
    // TODO: create an CompressionHeaderEncodingMap
    // TODO: validate that the strategy encoding path is "default" in this code path
    public CompressionHeaderEncodingMap(final CRAMEncodingStrategy encodingStrategy) {
        // NOTE: all of these encodings use external blocks and compressors for actual CRAM
        // data. The only use of core block encodings are as params for other (external)
        // encodings, i.e., the ByteArrayLenEncoding used for tag data uses a core (sub-)encoding
        // to store the length of the array that is stored in an external block.
        addExternalRansOrderZeroEncoding(DataSeries.AP_AlignmentPositionOffset);
        addExternalRansOrderOneEncoding(DataSeries.BA_Base);
        // the BB data series is not used by this implementation when writing CRAMs
        addExternalRansOrderOneEncoding(DataSeries.BF_BitFlags);
        addExternalGzipEncoding(encodingStrategy, DataSeries.BS_BaseSubstitutionCode);
        addExternalRansOrderOneEncoding(DataSeries.CF_CompressionBitFlags);
        addExternalGzipEncoding(encodingStrategy, DataSeries.DL_DeletionLength);
        addExternalGzipEncoding(encodingStrategy, DataSeries.FC_FeatureCode);
        addExternalGzipEncoding(encodingStrategy, DataSeries.FN_NumberOfReadFeatures);
        addExternalGzipEncoding(encodingStrategy, DataSeries.FP_FeaturePosition);
        addExternalGzipEncoding(encodingStrategy, DataSeries.HC_HardClip);
        addExternalByteArrayStopTabGzipEncoding(encodingStrategy, DataSeries.IN_Insertion);
        addExternalGzipEncoding(encodingStrategy, DataSeries.MF_MateBitFlags);
        addExternalGzipEncoding(encodingStrategy, DataSeries.MQ_MappingQualityScore);
        addExternalGzipEncoding(encodingStrategy, DataSeries.NF_RecordsToNextFragment);
        addExternalGzipEncoding(encodingStrategy, DataSeries.NP_NextFragmentAlignmentStart);
        addExternalRansOrderOneEncoding(DataSeries.NS_NextFragmentReferenceSequenceID);
        addExternalGzipEncoding(encodingStrategy, DataSeries.PD_padding);
        // the QQ data series is not used by this implementation when writing CRAMs
        addExternalRansOrderOneEncoding(DataSeries.QS_QualityScore);
        addExternalRansOrderOneEncoding(DataSeries.RG_ReadGroup);
        addExternalRansOrderZeroEncoding(DataSeries.RI_RefId);
        addExternalRansOrderOneEncoding(DataSeries.RL_ReadLength);
        addExternalByteArrayStopTabGzipEncoding(encodingStrategy, DataSeries.RN_ReadName);
        addExternalGzipEncoding(encodingStrategy, DataSeries.RS_RefSkip);
        addExternalByteArrayStopTabGzipEncoding(encodingStrategy, DataSeries.SC_SoftClip);
        addExternalGzipEncoding(encodingStrategy, DataSeries.TC_TagCount);
        addExternalGzipEncoding(encodingStrategy, DataSeries.TL_TagIdList);
        addExternalGzipEncoding(encodingStrategy, DataSeries.TN_TagNameAndType);
        addExternalRansOrderOneEncoding(DataSeries.TS_InsertSize);
    }

    /**
     * Constructor used to create an encoding map when reading a CRAM.
     * @param inputStream the CRAM input stream to be consumed
     */
    public CompressionHeaderEncodingMap(final InputStream inputStream) {
        final int byteSize = ITF8.readUnsignedITF8(inputStream);
        final byte[] bytes = new byte[byteSize];
        InputStreamUtils.readFully(inputStream, bytes, 0, bytes.length);
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);

        final int mapSize = ITF8.readUnsignedITF8(buffer);

        for (int i = 0; i < mapSize; i++) {
            final String dataSeriesAbbreviation = new String(new byte[]{buffer.get(), buffer.get()});
            final DataSeries dataSeries = DataSeries.byCanonicalName(dataSeriesAbbreviation);

            final EncodingID id = EncodingID.values()[buffer.get()];
            final int paramLen = ITF8.readUnsignedITF8(buffer);
            final byte[] paramBytes = new byte[paramLen];
            buffer.get(paramBytes);

            // NOTE: the compression associated with this data series is a property of the BLOCK in
            // which it resides, so the externalCompressors map isn't populated here. The block data
            // will be uncompressed before the encoding ever sees it.
            encodingMap.put(dataSeries, new EncodingDescriptor(id, paramBytes));
        }
    }

    /**
     * Create an encoding map from a previously serialized encoding map.
     * @param serializedMap
     */
    public CompressionHeaderEncodingMap(final CRAMEncodingMapJSON serializedMap) {
        if (serializedMap.getEncodingMapVersion() != CompressionHeaderEncodingMap.serialVersionUID) {
            // if the encoding map version doesn't match this implementation's version, the serialized map can't be used
            throw new IllegalArgumentException(
                    String.format(
                            "The provided serialized encoding map version (%d) that does not match the version of this implementation (%d)",
                            serializedMap.getEncodingMapVersion(),
                            CompressionHeaderEncodingMap.serialVersionUID));
        } else if (!serializedMap.getTargetCRAMVersion().equals(CramVersions.DEFAULT_CRAM_VERSION)) {
            // if the encoding map targets a different CRAM version than the one we're about to write, it can't be used
            throw new IllegalArgumentException(
                    String.format(
                            "The provided serialized encoding map was created for different version of CRAM (%s) than this version (%d)",
                            serializedMap.getTargetCRAMVersion(),
                            CramVersions.DEFAULT_CRAM_VERSION));
        }

        serializedMap.entries.forEach(e -> {
            this.encodingMap.put(e.dataSeries, e.encodingDescriptor);
            this.externalCompressors.put(e.contentID, ExternalCompressor.getCompressorForMethod(e.compressionMethod, e.compressorSpecificArg));
        });
    }

    /**
     * Add an external compressor for a tag block
     * @param tagId the tag as a content ID
     * @param compressor compressor to be used for this tag block
     */
    public void addTagBlockCompression(final int tagId, final ExternalCompressor compressor) {
        ValidationUtils.validateArg(
                Arrays.asList(DataSeries.values()).stream().noneMatch(ds -> ds.getExternalBlockContentId().intValue() == tagId),
                String.format("tagID %d overlaps with data series content ID", tagId));
        externalCompressors.put(tagId, compressor);
    }

    /**
     * Get the encoding params that should be used for a given DataSeries.
     * @param dataSeries
     * @return EncodingDescriptor for the DataSeries
     */
    public EncodingDescriptor getEncodingDescriptorForDataSeries(final DataSeries dataSeries) {
        return encodingMap.get(dataSeries);
    }

    /**
     * Get a list of all external IDs for this encoding map
     * @return list of all external IDs for this encoding map
     */
    public List<Integer> getExternalIDs() { return new ArrayList(externalCompressors.keySet()); }

    /**
     * Given a content ID, return a {@link Block} for that ID by obtaining the contents of the stream,
     * compressing it using the compressor for that contentID, and converting the result to a {@link Block}.
     * @param contentId contentID to use
     * @param outputStream stream to compress
     * @return Block containing the compressed contends of the stream
     */
    public Block createCompressedBlockForStream(final Integer contentId, final ByteArrayOutputStream outputStream) {
        final ExternalCompressor compressor = externalCompressors.get(contentId);
        final byte[] rawContent = outputStream.toByteArray();
        return Block.createExternalBlock(
                compressor.getMethod(),
                contentId,
                compressor.compress(rawContent),
                rawContent.length);
    }

    /**
     * Write the encoding map out to a CRAM Stream
     * @param outputStream
     * @throws IOException
     */
    public void write(final OutputStream outputStream) throws IOException {
        // encoding map:
        int size = 0;
        for (final DataSeries dataSeries : encodingMap.keySet()) {
            // not all DataSeries are used by this implementation
            if (encodingMap.get(dataSeries).getEncodingID() != EncodingID.NULL) {
                size++;
            }
        }

        //TODO: what is the correct allocation size here....
        final ByteBuffer mapBuffer = ByteBuffer.allocate(1024 * 100);
        ITF8.writeUnsignedITF8(size, mapBuffer);
        for (final DataSeries dataSeries : encodingMap.keySet()) {
            if (encodingMap.get(dataSeries).getEncodingID() == EncodingID.NULL) {
                // not all DataSeries are used by this implementation
                continue;
            }

            final String dataSeriesAbbreviation = dataSeries.getCanonicalName();
            mapBuffer.put((byte) dataSeriesAbbreviation.charAt(0));
            mapBuffer.put((byte) dataSeriesAbbreviation.charAt(1));

            final EncodingDescriptor params = encodingMap.get(dataSeries);
            mapBuffer.put((byte) (0xFF & params.getEncodingID().getId()));
            ITF8.writeUnsignedITF8(params.getEncodingParameters().length, mapBuffer);
            mapBuffer.put(params.getEncodingParameters());
        }
        mapBuffer.flip();
        final byte[] mapBytes = new byte[mapBuffer.limit()];
        mapBuffer.get(mapBytes);

        ITF8.writeUnsignedITF8(mapBytes.length, outputStream);
        outputStream.write(mapBytes);
    }

    /**
     * Constructor used to create an encoding map from a serialized JSON file when writing a CRAM.
     * @param encodingMapPath the CRAM input stream to be consumed
     */
    public static CompressionHeaderEncodingMap readFromPath(final Path encodingMapPath) {
        try (final BufferedReader fr = Files.newBufferedReader(encodingMapPath)) {
            final Gson gson = new Gson();
            final CRAMEncodingMapJSON serializedMap = gson.fromJson(fr, CRAMEncodingMapJSON.class);
            final CompressionHeaderEncodingMap encodingMap = new CompressionHeaderEncodingMap(serializedMap);
            return encodingMap;
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed reading json file for encoding map", e);
        }
    }

    /**
     * Constructor used to write a serialized (JSON) encoding map to record the encoding map
     * used to create a particular CRAM stream..
     * @param encodingMapPath the path to an encoding map JSON file
     */
    public void writeToPath(final Path encodingMapPath) {
        try (final BufferedWriter fileWriter = Files.newBufferedWriter(encodingMapPath)) {
            final GsonBuilder gsonBuilder = new GsonBuilder().enableComplexMapKeySerialization().setPrettyPrinting();

            // created a CRAMEncodingMapJSON, and populate it with all encoding entries
            final CRAMEncodingMapJSON serializedMap = new CRAMEncodingMapJSON(serialVersionUID, CramVersions.DEFAULT_CRAM_VERSION);
            encodingMap.entrySet().forEach(
                    e -> serializedMap.addEncodingMapEntry(
                            new CRAMEncodingMapJSON.CRAMEncodingMapJSONEntry(
                                    e.getKey().getExternalBlockContentId(),
                                    e.getKey(),
                                    e.getValue(),
                                    externalCompressors.get(e.getKey().getExternalBlockContentId())))
            );

            final Gson gson = gsonBuilder.create();
            final String jsonEncodingString = gson.toJson(serializedMap, CRAMEncodingMapJSON.class);
            fileWriter.write(jsonEncodingString);
        } catch (final IOException e) {
            throw new RuntimeIOException("Failed writing json file for encoding map", e);
        }
    }

    private void addExternalByteArrayStopTabGzipEncoding(final CRAMEncodingStrategy encodingStrategy, final DataSeries dataSeries) {
        addExternalEncoding(dataSeries,
                new ByteArrayStopEncoding((byte) '\t', dataSeries.getExternalBlockContentId()).toEncodingDescriptor(),
                new GZIPExternalCompressor(encodingStrategy.getGZIPCompressionLevel()));
    }

    // add an external encoding appropriate for the dataSeries value type, with a GZIP compressor
    private void addExternalGzipEncoding(final CRAMEncodingStrategy encodingStrategy, final DataSeries dataSeries) {
        addExternalEncoding(dataSeries, new GZIPExternalCompressor(encodingStrategy.getGZIPCompressionLevel()));
    }

    // add an external encoding appropriate for the dataSeries value type, with a RANS order 1 compressor
    private void addExternalRansOrderOneEncoding(final DataSeries dataSeries) {
        addExternalEncoding(dataSeries, new RANSExternalCompressor(RANS.ORDER.ONE));
    }

    // add an external encoding appropriate for the dataSeries value type, with a RANS order 0 compressor
    private void addExternalRansOrderZeroEncoding(final DataSeries dataSeries) {
        addExternalEncoding(dataSeries, new RANSExternalCompressor(RANS.ORDER.ZERO));
    }

    // add an external encoding and corresponding compressor
    private void addExternalEncoding(final DataSeries dataSeries,
                                     final EncodingDescriptor encodingDescriptor,
                                     final ExternalCompressor compressor) {
        externalCompressors.put(dataSeries.getExternalBlockContentId(), compressor);
        addEncoding(dataSeries, encodingDescriptor);
    }

    // Visible for testing, because without this we have no way to unit test round-tripping an
    // encoding map that contains the handful of data series that htsjdk generally doesn't use
    // when writing, since there is no code to add those data series to the map as part of the
    // CRAM write implementation.
    void addExternalEncoding(final DataSeries dataSeries, final ExternalCompressor compressor) {
        // This spins up a CRAMEncoding temporarily in order to retrieve its EncodingDescriptor.
        // In reality, the encoding descriptor/parameters for each of these external encoding
        // classes happens to be identical and are all interchangeable (they only contain the
        // content ID and nothing else, no matter  what the data series type), but that's
        // accidental and could change, so don't rely on it.
        final int blockContentID = dataSeries.getExternalBlockContentId();
        CRAMEncoding<?> cramEncoding;
        switch (dataSeries.getType()) {
            case BYTE:
                cramEncoding = new ExternalByteEncoding(blockContentID);
                break;
            case INT:
                cramEncoding = new ExternalIntegerEncoding(blockContentID);
                break;
            case LONG:
                cramEncoding = new ExternalLongEncoding(blockContentID);
                break;
            case BYTE_ARRAY:
                cramEncoding = new ExternalByteEncoding(blockContentID);
                break;
            default:
                throw new CRAMException("Unknown data series value type");
        }
        addExternalEncoding(dataSeries, cramEncoding.toEncodingDescriptor(), compressor);
    }

    // May be either EXTERNAL or CORE. If EXTERNAL, the caller should also add an appropriate compressor
    // to the externalCompressor map.
    private void addEncoding(final DataSeries dataSeries, final EncodingDescriptor encodingDescriptor) {
        encodingMap.put(dataSeries, encodingDescriptor);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CompressionHeaderEncodingMap that = (CompressionHeaderEncodingMap) o;

        if (!this.encodingMap.equals(that.encodingMap)) return false;
        return this.externalCompressors.equals(that.externalCompressors);
    }

    @Override
    public int hashCode() {
        int result = encodingMap.hashCode();
        result = 31 * result + externalCompressors.hashCode();
        return result;
    }

}