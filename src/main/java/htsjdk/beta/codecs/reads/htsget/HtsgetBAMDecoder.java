package htsjdk.beta.codecs.reads.htsget;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormat;

public abstract class HtsgetBAMDecoder implements ReadsDecoder {
    protected final Bundle inputBundle;
    protected final ReadsDecoderOptions readsDecoderOptions;
    private final String displayName;

    public HtsgetBAMDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        this.inputBundle = inputBundle;
        this.readsDecoderOptions = readsDecoderOptions;
        this.displayName = inputBundle.get(BundleResourceType.READS).get().getDisplayName();
    }

    @Override
    final public ReadsFormat getFormat() { return ReadsFormat.BAM; }

    @Override
    public HtsCodecVersion getVersion() {
        return HtsgetBAMCodec.HTSGET_VERSION;
    }

    @Override
    final public String getDisplayName() { return displayName; }

}