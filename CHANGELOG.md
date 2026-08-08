## Version 2.1.2

### Fixed
- Fixed rollers with a shuffle filter placing single-material "pillars" in fill modes. Each block in a fill column is now chosen independently (per position, including height) instead of reusing one material for the whole column, so fills are random vertically as well as horizontally.

## Version 2.1.1

### Fixed
- Fixed shuffle filter items being rejected when travelling through smart chutes and other filtering blocks as regular cargo.
