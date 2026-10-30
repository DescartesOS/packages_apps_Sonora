# Sonora for DescartesOS

Developer: [lostsignal-502](https://github.com/lostsignal-502).

Sonora is a DescartesOS music client built on ArchiveTune, with a custom setup,
listening hub, destination navigation, Lyrics studio, and artwork-backed player
glass. Existing saved playback and appearance preferences remain respected.

## Credits

- Rukamori and ArchiveTune contributors: application framework, playback, library,
  integrations, and the upstream foundation. Original copyright and license
  notices are retained.
- kushagrasinghx and BitChord contributors: lightweight glass/rim approach,
  adapted to Sonora's existing renderer. See `scripts/BITCHORD_PORT.md`.
- maxrave-dev and SimpMusic contributors: inline lyrics timing adjustment,
  adapted to Sonora's rendering and persistence. See `scripts/SIMP_MUSIC_PORT.md`.
- Additional upstream and dependency credits remain in Settings → About.

ArchiveTune's donation/star prompt and product support links are removed from
Sonora's setup and About page. The inherited upstream updater is disabled.

Build checks: `scripts/verify-sonora.sh`.
Phone checks: `scripts/SONORA_PHONE_TESTING.md`.

Licensed under GPL-3.0; see LICENSE. This is a modified derivative of ArchiveTune.
