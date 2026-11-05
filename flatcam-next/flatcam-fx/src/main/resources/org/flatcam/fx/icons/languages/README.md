# Language selector SVGs

Source: [HatScripts/circle-flags](https://github.com/HatScripts/circle-flags),
commit `379588b5da95482d6bbf10bd45644a35b0609ea6`, MIT license.
`source.json` identifies original paths and SHA-256 hashes.

- `pt_BR.svg`: upstream `flags/br.svg`, unchanged.
- `es.svg`: upstream `flags/es.svg`, unchanged.
- `fr.svg`: upstream `flags/fr.svg`, unchanged.
- `en.svg`: FX composition of upstream `flags/us.svg` and `flags/gb.svg`,
  clipped diagonally into one circular badge. Original path geometry and
  palette retained. This composite is an FX adaptation, not an upstream icon.

Copyright and the full upstream license are bundled in
`../../about/circle-flags-LICENSE.txt` and shown in Help > About > License.
The application loads local SVG paths as native JavaFX vectors; no CDN,
JavaScript, emoji font, raster conversion or additional dependency is used.
