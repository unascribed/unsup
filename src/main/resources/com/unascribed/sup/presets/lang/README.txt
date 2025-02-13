Filenames are IETF BCP 47 language tags. These are not exactly the same as Minecraft's language
codes. Most of them are the same, just with a hyphen (-) instead of an underscore (_).

Note that Puppet uses either Swing or raw FreeType2 for font rendering, which do not support complex
font shaping such as combining diacritics or extended grapheme clusters. If you're used to localizing
for Minecraft, then this won't affect you.

The OpenGL puppet presently does not support RTL languages - the font rendering engine needs to be
reworked to use HarfBuzz instead of raw FreeType2. You can force the Swing puppet for your language
by adding "puppet_mode=swing" before the "[strings]" line in your language file.
