package htsjdk.samtools.cram.structure;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import org.testng.annotations.DataProvider;

import java.util.HashSet;
import java.util.Set;

public class StructureTestUtils extends HtsjdkTest {

    // Set of DataSeries that HTSJDK doesn't generate on write, but which may be present in
    // CRAM streams generated by other implementations. Used for synthesizing these encodings
    // for test purposes, and for filtering out the set of expected data series and encodings
    // in tests.
    public static final Set<DataSeries> DATASERIES_NOT_WRITTEN_BY_HTSJDK = new HashSet<DataSeries>() {{
        add(DataSeries.TV_TestMark);
        add(DataSeries.TM_TestMark);
        add(DataSeries.BB_bases);
        add(DataSeries.QQ_scores);
    }};

    @DataProvider(name="externalCompressionMethods")
    public Object[] getExternalCompressionMethods() {
        return BlockCompressionMethod.values();
    }

}