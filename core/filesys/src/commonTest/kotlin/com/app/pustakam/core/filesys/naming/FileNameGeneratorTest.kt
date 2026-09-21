package com.app.pustakam.core.filesys.naming

import com.app.pustakam.core.common.util.ContentType
import com.app.pustakam.core.filesys.export.ExportFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// 🔧 30-Jul-2026 02:10 Phase 1 — pins every filename rule. A change here renames files on devices.
class FileNameGeneratorTest {

    private val ts = 1_753_832_400_000L

    // ---- generate (capture) ----

    @Test
    fun capture_name_is_timestamp_plus_extension() {
        assertEquals("${ts}.png", FileNameGenerator.generate(ContentType.IMAGE, ts))
        assertEquals("${ts}.mp4", FileNameGenerator.generate(ContentType.VIDEO, ts))
        assertEquals("${ts}.epub", FileNameGenerator.generate(ContentType.EPUB, ts))
    }

    // 🎧 a recording is AAC in MP4 on both platforms — named .m4a so every player and the server agree
    @Test
    fun capture_name_for_a_recording_is_m4a() {
        assertEquals("${ts}.m4a", FileNameGenerator.generate(ContentType.AUDIO, ts))
    }

    @Test
    fun capture_name_for_an_extensionless_type_is_just_the_timestamp() {
        assertEquals("$ts", FileNameGenerator.generate(ContentType.OTHER, ts))
    }

    // ---- sanitize ----

    @Test
    fun sanitize_replaces_path_separators() {
        assertEquals("a_b_c.pdf", FileNameGenerator.sanitize("a/b/c.pdf"))
        assertEquals("a_b.pdf", FileNameGenerator.sanitize("a\\b.pdf"))
    }

    @Test
    fun sanitize_keeps_ordinary_names_untouched() {
        assertEquals("Quarterly Report.pdf", FileNameGenerator.sanitize("Quarterly Report.pdf"))
        assertEquals("note-1_v2.md", FileNameGenerator.sanitize("note-1_v2.md"))
    }

    @Test
    fun sanitize_falls_back_for_blank_input() {
        assertEquals("file", FileNameGenerator.sanitize(""))
        assertEquals("file", FileNameGenerator.sanitize("    "))
    }

    @Test
    fun sanitize_caps_a_very_long_base_but_keeps_the_extension() {
        val long = "x".repeat(500) + ".pdf"
        val out = FileNameGenerator.sanitize(long)
        assertTrue(out.endsWith(".pdf"), "extension must survive: $out")
        assertEquals(FileNameGenerator.MAX_BASE_LENGTH + ".pdf".length, out.length)
    }

    @Test
    fun sanitize_caps_a_long_name_that_has_no_extension() {
        val out = FileNameGenerator.sanitize("y".repeat(500))
        assertEquals(FileNameGenerator.MAX_BASE_LENGTH, out.length)
    }

    @Test
    fun sanitize_treats_a_leading_dot_as_part_of_the_name_not_an_extension() {
        assertEquals(".gitignore", FileNameGenerator.sanitize(".gitignore"))
    }

    // ---- generateUnique (duplicate names) ----

    @Test
    fun unique_name_is_the_original_when_the_folder_is_empty() {
        assertEquals("a.pdf", FileNameGenerator.generateUnique("a.pdf", ts) { false })
    }

    @Test
    fun unique_name_gets_a_timestamp_prefix_on_first_collision() {
        val taken = setOf("a.pdf")
        assertEquals("${ts}_a.pdf", FileNameGenerator.generateUnique("a.pdf", ts) { it in taken })
    }

    @Test
    fun unique_name_adds_a_counter_when_the_prefixed_name_is_also_taken() {
        val taken = setOf("a.pdf", "${ts}_a.pdf", "${ts}_1_a.pdf")
        assertEquals("${ts}_2_a.pdf", FileNameGenerator.generateUnique("a.pdf", ts) { it in taken })
    }

    @Test
    fun unique_name_sanitizes_before_testing_for_collisions() {
        assertEquals("a_b.pdf", FileNameGenerator.generateUnique("a/b.pdf", ts) { false })
    }

    // ---- suggestExportName ----

    @Test
    fun export_name_keeps_alphanumerics_dash_and_underscore() {
        assertEquals("My-Note_1-$ts.pdf", FileNameGenerator.suggestExportName("My-Note_1", ExportFormat.PDF, ts))
    }

    @Test
    fun export_name_replaces_every_other_character() {
        assertEquals("a_b_c-$ts.png", FileNameGenerator.suggestExportName("a/b c", ExportFormat.IMAGE, ts))
    }

    @Test
    fun export_name_falls_back_to_note_for_blank_titles() {
        assertEquals("note-$ts.docx", FileNameGenerator.suggestExportName(null, ExportFormat.DOCX, ts))
        assertEquals("note-$ts.docx", FileNameGenerator.suggestExportName("   ", ExportFormat.DOCX, ts))
    }

    @Test
    fun export_name_replaces_non_ascii_letters_like_the_old_regex_did() {
        // Char.isLetterOrDigit() would KEEP these and silently rename exports for non-ASCII titles.
        assertEquals("_n_cod_-$ts.pdf", FileNameGenerator.suggestExportName("Ünïcodé", ExportFormat.PDF, ts))
        assertEquals("____-$ts.pdf", FileNameGenerator.suggestExportName("日本語だ", ExportFormat.PDF, ts))
    }

    @Test
    fun export_name_caps_an_enormous_title() {
        val out = FileNameGenerator.suggestExportName("t".repeat(1000), ExportFormat.PDF, ts)
        assertTrue(out.endsWith(".pdf"))
        assertTrue(out.length <= FileNameGenerator.MAX_BASE_LENGTH + 32, "too long: ${out.length}")
    }

    // ---- suggestSaveName ----

    @Test
    fun save_name_prefers_a_source_file_that_already_has_an_extension() {
        assertEquals(
            "Video-2.mp4",
            FileNameGenerator.suggestSaveName("ignored", "/data/x/Video-2.mp4", ContentType.VIDEO, ts),
        )
    }

    @Test
    fun save_name_uses_the_title_plus_the_type_extension() {
        assertEquals(
            "Holiday.png",
            FileNameGenerator.suggestSaveName("Holiday", null, ContentType.IMAGE, ts),
        )
    }

    @Test
    fun save_name_falls_back_to_a_timestamped_default() {
        assertEquals(
            "Pustakam-$ts.mp3",
            FileNameGenerator.suggestSaveName(null, null, ContentType.AUDIO, ts),
        )
    }

    // ---- thumbnailName ----

    @Test
    fun thumbnail_name_appends_thumb_and_forces_jpg() {
        assertEquals("photo_thumb.jpg", FileNameGenerator.thumbnailName("/data/x/photo.png"))
        assertEquals("clip_thumb.jpg", FileNameGenerator.thumbnailName("clip.mp4"))
    }

    // ---- fromUrl ----

    @Test
    fun url_name_uses_the_last_segment_when_it_has_an_extension() {
        assertEquals("file.pdf", FileNameGenerator.fromUrl("https://a.com/d/file.pdf", null, ts))
    }

    @Test
    fun url_name_strips_query_and_fragment() {
        assertEquals("file.pdf", FileNameGenerator.fromUrl("https://a.com/file.pdf?x=1#y", null, ts))
    }

    @Test
    fun url_name_appends_the_mime_extension_when_the_url_has_none() {
        // The Android-only "No file found" case from the FIA doc §3.3.
        assertEquals("download-$ts.pdf", FileNameGenerator.fromUrl("https://a.com/download?id=1", "application/pdf", ts))
    }

    @Test
    fun url_name_has_no_extension_when_the_mime_is_unknown_too() {
        val out = FileNameGenerator.fromUrl("https://a.com/download", MimeCatalogStub.UNKNOWN, ts)
        assertEquals("download-$ts", out)
        assertFalse(out.contains('.'))
    }

    private object MimeCatalogStub { const val UNKNOWN = "application/x-unknown-thing" }
}
