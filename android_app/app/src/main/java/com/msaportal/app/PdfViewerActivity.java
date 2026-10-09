package com.msaportal.app;

public class PdfViewerActivity extends android.app.Activity {
    public static final String EXTRA_PDF_PATH = "msa_pdf_path";

    private android.os.ParcelFileDescriptor fileDescriptor;
    private android.graphics.pdf.PdfRenderer renderer;

    @Override
    protected void onCreate(android.os.Bundle state) {
        super.onCreate(state);

        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (12 * density);

        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(android.graphics.Color.WHITE);

        android.widget.LinearLayout toolbar =
                new android.widget.LinearLayout(this);
        toolbar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolbar.setPadding(pad, pad, pad, pad);
        toolbar.setBackgroundColor(android.graphics.Color.rgb(20, 55, 95));

        android.widget.Button back = new android.widget.Button(this);
        back.setText("← Back");
        back.setTextColor(android.graphics.Color.WHITE);
        back.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        android.graphics.Color.rgb(20, 55, 95)
                )
        );
        back.setOnClickListener(v -> finish());

        android.widget.TextView heading = new android.widget.TextView(this);
        heading.setText("Mwashita Academy — Result PDF");
        heading.setTextColor(android.graphics.Color.WHITE);
        heading.setTextSize(16);
        heading.setPadding(pad, 0, 0, 0);

        toolbar.addView(back);
        toolbar.addView(heading);
        root.addView(toolbar);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout pages =
                new android.widget.LinearLayout(this);
        pages.setOrientation(android.widget.LinearLayout.VERTICAL);
        pages.setPadding(pad, pad, pad, pad);
        scroll.addView(pages);

        root.addView(scroll, new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1
        ));
        setContentView(root);

        try {
            String path = getIntent().getStringExtra(EXTRA_PDF_PATH);
            if (path == null || path.isEmpty()) {
                throw new java.io.IOException("PDF path is missing");
            }

            fileDescriptor = android.os.ParcelFileDescriptor.open(
                    new java.io.File(path),
                    android.os.ParcelFileDescriptor.MODE_READ_ONLY
            );
            renderer = new android.graphics.pdf.PdfRenderer(fileDescriptor);

            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            int pageWidth = Math.max(200, screenWidth - 2 * pad);

            for (int i = 0; i < renderer.getPageCount(); i++) {
                android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(i);
                try {
                    int pageHeight = Math.max(
                            1,
                            pageWidth * page.getHeight() / page.getWidth()
                    );

                    android.graphics.Bitmap bitmap =
                            android.graphics.Bitmap.createBitmap(
                                    pageWidth, pageHeight,
                                    android.graphics.Bitmap.Config.ARGB_8888
                            );
                    bitmap.eraseColor(android.graphics.Color.WHITE);

                    page.render(
                            bitmap, null, null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    );

                    android.widget.ImageView image =
                            new android.widget.ImageView(this);
                    image.setImageBitmap(bitmap);
                    image.setAdjustViewBounds(true);
                    image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);

                    android.widget.LinearLayout.LayoutParams params =
                            new android.widget.LinearLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                    params.bottomMargin = pad;
                    pages.addView(image, params);
                } finally {
                    page.close();
                }
            }

            if (renderer.getPageCount() == 0) {
                throw new java.io.IOException("The PDF has no pages");
            }

        } catch (Exception e) {
            android.widget.Toast.makeText(
                    this,
                    "Unable to display PDF: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG
            ).show();
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (renderer != null) {
            renderer.close();
            renderer = null;
        }
        if (fileDescriptor != null) {
            try {
                fileDescriptor.close();
            } catch (java.io.IOException ignored) {
            }
            fileDescriptor = null;
        }
        super.onDestroy();
    }
}
