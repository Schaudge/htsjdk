package htsjdk.beta.codecs.variants.vcf.vcfv4_2;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;

import java.io.OutputStream;

import static htsjdk.variant.variantcontext.writer.Options.INDEX_ON_THE_FLY;

public class VCFEncoderV4_2 extends VCFEncoder {
    private VariantContextWriter vcfWriter;

    public VCFEncoderV4_2(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsCodecVersion getVersion() {
        return VCFCodecV4_2.VCF_V42_VERSION;
    }

    @Override
    public void setHeader(final VCFHeader vcfHeader) {
        vcfWriter = getVCFWriter(variantsEncoderOptions, vcfHeader);
        vcfWriter.writeHeader(vcfHeader);
    }

    @Override
    public void write(final VariantContext variantContext) {
        vcfWriter.add(variantContext);
    }

    @Override
    public void close() {
        if (vcfWriter != null) {
            vcfWriter.close();
        }
    }

    private VariantContextWriter getVCFWriter(final VariantsEncoderOptions variantsEncoderOptions, final VCFHeader vcfHeader) {
        final BundleResource variantsResource = outputBundle.get(BundleResourceType.VARIANTS).get();
        if (variantsResource.getIOPath().isPresent()) {
            final VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
            return builder
                    .setOutputPath(variantsResource.getIOPath().get().toPath())
                    .unsetOption(INDEX_ON_THE_FLY)
                    .build();
        } else {
            throw new IllegalArgumentException("VCF writer to stream not yet implemented");
        }
    }
}