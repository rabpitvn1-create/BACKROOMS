# Ornate narrative UI v1

Generated with the built-in ImageGen tool on 2026-10-11 from the user's
`170.png` visual reference. Both original RGBA PNGs were uploaded to
[Novel/ui](https://drive.google.com/drive/folders/1i2wLHXMmFj3WXX12OryZCM1VaIG_baLb)
and read back before integration. Assets are packaged locally for offline use.

- [Narrative frame](https://drive.google.com/file/d/1MlCT84hQuFqzzIeqUwozMAweIOnCtZWB/view):
  `backrooms-narrative-frame-v1.png`, 1254 × 1254, transparent center.
- [Level nameplate](https://drive.google.com/file/d/1_urOZhnj8z8xdDaIN2q4IBdWzhea3ODd/view):
  `backrooms-level-nameplate-v1.png`, 2172 × 724, transparent exterior.

Generation briefs: isolated antique muted gold double-border narrative frame,
cloud/floral scrollwork concentrated in opposite corners, empty transparent
center suitable for nine-slice rendering; matching wide empty dark charcoal
Level plaque with gold outlines and ornamental wings. No baked text, scenes,
numbers, or watermarks. Live titles and narrative remain DOM text.

`ornate-frame-v1.css` slices the narrative border at 32% with 40px rendered
corners; the scrollable `#log` stays inside a stationary decorative wrapper.
The plaque uses the original image's transparent vertical margins via centered
background sizing. Warning messages retain amber emphasis. No gameplay or save
contracts are changed.
