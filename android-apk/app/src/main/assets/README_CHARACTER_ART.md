# Character artwork assets

This file is the local source of truth for importing character artwork into this repository.
Do not inspect another repository or an old PR just to find the Drive file IDs or destination paths.

## Source folder

Google Drive folder:

`https://drive.google.com/drive/folders/15OEQZUvfdKS_z9iwoLv-rSjHl9Q1aF5P`

Artwork is imported as the original file bytes. Do not resize, recompress, convert, crop, or rename the Drive source before copying it into the repository.

## Current Drive mappings

| Character / use | Drive source | Drive file ID | Repository asset | Runtime status |
| --- | --- | --- | --- | --- |
| Cao Minh avatar | `CAO_MINH_AVATAR.jpg` | `1IPHbd4FfFfP3ppVj571KCb-mC3tJUzie` | `avatars/cao_minh_avatar.jpg` | wired |
| Lucia Lục overlay | `LUCIA_LUC_OVERLAY.png` | `1cM_6OQ3LbQwZXX_uU3vdOK2JLxiL2w9l` | `lucia_overlay.png` | wired |
| Syvial overlay | `Syvial.png` | `1JJK9FilclU25446m2ABjOKb5zEJzBE-X` | `syvial_overlay.png` | wired |
| Lục Trầm overlay | `LUC_TRAM_OVERLAY.png` | `1M876xffPaGaYY8k2waX5h1ff5JcmxJVp` | `luctram_overlay.png` | wired for Lục Trầm Follower |
| Lục Trầm avatar | `LUC_TRAM_AVATAR.png` | `1qauFB8EC6jDhVPOxVdrlXU_PLUFa_GHk` | `avatars/luctram_avatar.png` | wired for Lục Trầm Follower |
| Lục Trầm hắc hoá Entity | `LUC_TRAM_HAC_HOA_OVERLAY.webp` | `15tYIjDJBoW7z-05Y6PgN8AaIV7hCkEw1` | `entity/luc_tram_hac_hoa.webp` | wired; 650,706 bytes; alpha preserved |

Existing Syvial portrait `avatars/Syvial_avatar.jpg` is a separate portrait asset. Do not replace it with the full-body overlay unless explicitly requested.

## Direct Drive -> GitHub import

For public/shared Drive files in the folder above, a temporary GitHub Action can download the exact source directly from Drive:

```bash
curl -fL --retry 3 --retry-all-errors --connect-timeout 20 \
  "https://drive.usercontent.google.com/download?id=<DRIVE_FILE_ID>&export=download&confirm=t" \
  -o "<DESTINATION_PATH>"
```

After downloading:

1. Verify the file type and expected byte size from Drive metadata.
2. Commit only the requested asset paths.
3. Fetch `origin/main` and compare the committed bytes against the downloaded source with `cmp -s` or a recorded SHA-256.
4. If the task is asset-only, grep runtime files and verify the new filename has no display/runtime reference.
5. Remove the temporary workflow after verification.

The temporary Action should have only `contents: write` unless another permission is actually required.

## Display wiring

Importing an asset and displaying it are separate changes.

Combat character overlays are selected in:

`app/src/main/assets/combat-93-snapshot.js`

Current explicit mappings include Cao Minh, Lucia Lục, and Syvial. Add a new character mapping only when requested, then add focused browser coverage in:

`tests/combat93-browser.cjs`

Character portraits/avatars are separate from combat overlays. Avatar references normally live in character/runtime records and should point under `assets/avatars/`.

Lục Trầm Follower explicitly uses `luctram_overlay.png` and `avatars/luctram_avatar.png`; Entity art stays separate.
