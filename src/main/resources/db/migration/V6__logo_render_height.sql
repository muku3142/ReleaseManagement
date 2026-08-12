-- D-05.1-01/D-05.1-02: the cover logo's RENDERED height is a per-template chrome setting, not a global
-- config value and not a constant hard-coded separately into each renderer (which is exactly how the
-- three renderers came to disagree by up to 4.5x). Millimetres because the output is a printed page:
-- openhtmltopdf accepts mm natively in CSS, 1mm = 36000 EMU exactly for docx4j, and CSS mm works in
-- the browser - one stored integer serves all three renderers with no lossy conversion.
--
-- D-05.1-04: DEFAULT 30 (= the PDF's previous hard-coded `max-height: 3cm`) so no existing template
-- and no existing document changes appearance. The one intentional visible change is DOCX, which had
-- no clamp at all and rendered a 512x512 logo 5.3in tall.
--
-- Bounds 5..120, and they must stay identical to ChromeFieldsRequest's @Min/@Max. Upper: A4 is 297mm
-- tall, PdfExportAdapter sets `@page margin: 2.5cm 2cm` leaving 247mm printable, and `.cover` already
-- spends 60mm on padding-top; the cover title (24pt, 0.6cm top margin) and subtitle (13pt) need about
-- 26mm below the logo. 60 + 120 + 26 = 206mm, 41mm inside the printable area, so no permitted value
-- can push cover content off page 1. Lower: 5mm keeps a logo visible instead of a zero-height ghost.
ALTER TABLE template ADD COLUMN logo_render_height_mm INTEGER NOT NULL DEFAULT 30
    CHECK (logo_render_height_mm BETWEEN 5 AND 120);

-- D-05.1-03: copied onto the snapshot at generation time, exactly like the four resolved chrome strings
-- and the logo bytes already are, so re-exporting revision 1 reproduces the size it was generated with
-- rather than today's template setting (DOC-07, and Phase 7's AUDIT-02 rests on it).
--
-- Unlike V5's other document_snapshot chrome columns this one carries a DEFAULT. That is a deliberate
-- exception to the "no default, generation always supplies them" convention above: rows generated
-- before this migration also need a value, and it has to be the one that reproduces today's PDF
-- behaviour (D-05.1-04 permits this and forbids backfilling anything else). The CHECK is repeated here
-- on purpose - the snapshot is the immutable audit record, and repeating it makes a forgotten setter in
-- the persistence mapper fail loudly (an unset Java int is 0, which this rejects) rather than silently
-- writing 0 and rendering an invisible logo forever.
ALTER TABLE document_snapshot ADD COLUMN logo_render_height_mm INTEGER NOT NULL DEFAULT 30
    CHECK (logo_render_height_mm BETWEEN 5 AND 120);
