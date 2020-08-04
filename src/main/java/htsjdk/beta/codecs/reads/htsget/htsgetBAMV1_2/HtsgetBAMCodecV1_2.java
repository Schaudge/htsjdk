package htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2;

import htsjdk.beta.codecs.reads.htsget.HtsgetBAMCodec;
import htsjdk.beta.codecs.reads.htsget.HtsgetBAMDecoder;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.beta.plugin.HtsRecord;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;

import java.util.Optional;

public class HtsgetBAMCodecV1_2 extends HtsgetBAMCodec {

    @Override
    public HtsgetBAMDecoder getDecoder(final Bundle inputBundle,
                                       final ReadsDecoderOptions decodeOptions) {
        final Optional<BundleResource> readsResource = inputBundle.get(BundleResourceType.READS);
        if (!readsResource.isPresent()) {
            throw new IllegalArgumentException("The input bundle contains no reads source");
        }
        final Optional<IOPath> inputPath = readsResource.get().getIOPath();
        if (!inputPath.isPresent()) {
            throw new IllegalArgumentException("The reads source must be a IOPath");
        }
        return new HtsgetBAMDecoderV1_2(inputBundle, decodeOptions);
    }

    @Override
    public HtsEncoder<ReadsFormat, ?, ? extends HtsRecord> getEncoder(Bundle outputBundle, ReadsEncoderOptions encodeOptions) {
        throw new IllegalArgumentException("Htsget is read only - no Htsget BAM encoder component is available.");
    }

}