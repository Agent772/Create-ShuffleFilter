# Create Shuffle Filter - Asset Information

## Item Assets

This mod includes all necessary assets for the Shuffle Filter item to work in-game:

### Model
- `models/item/shuffle_filter.json` - Defines the item model using Minecraft's standard generated item parent

### Texture
- `textures/item/shuffle_filter.png` - A 16x16 placeholder texture with a purple/blue design
  - **Note**: This is a basic placeholder texture. You may want to replace it with a more polished design that better matches Create's visual style.
  - The texture shows a paper-like background with blue vertical lines and red horizontal accents to suggest the "shuffle" concept.

### Localization
- `lang/en_us.json` - Contains the display name "Shuffle Filter" for the item

## GUI Functionality

The Shuffle Filter inherits its GUI functionality from Create's `FilterItem` class:
- Right-clicking the item opens the standard Create filter configuration screen
- The filter type is set to `FilterType.REGULAR`, making it behave like Create's List Filter
- All filter operations (adding/removing items, setting whitelist/blacklist mode) work automatically

## In-Game Availability

The item is available in two ways:
1. **Creative Tab**: Found in the "Create Shuffle Filter" creative mode tab
2. **Crafting**: Two recipe variants allow crafting with:
   - Iron Nugget + Any Wool + Brass Nugget
   - Brass Nugget + Any Wool + Iron Nugget

## Testing

To test the item in-game:
1. Launch Minecraft with this mod and Create installed
2. Open the creative inventory and search for "Shuffle Filter"
3. Right-click the item to open the filter GUI
4. The GUI should match Create's standard List Filter interface

## Customization

If you want to customize the texture:
1. Edit `textures/item/shuffle_filter.png` with your preferred image editor
2. Keep it as a 16x16 PNG with transparency (RGBA)
3. Consider matching Create's art style for visual consistency
