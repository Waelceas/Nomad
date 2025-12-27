# Nomad Plugin Documentation

## Overview

Nomad is a Minecraft plugin that implements a traveling merchant system with daily rotating items. The plugin provides a GUI-based shop interface where players can purchase specially selected items that change daily.

## Features

- **Daily Rotating Shop**: Items automatically refresh based on configurable schedule
- **GUI-based Interface**: User-friendly inventory menu for shopping
- **Economy Integration**: Vault-compatible economy system for transactions
- **Admin Management**: Comprehensive commands for managing items and settings
- **Customizable**: Full configuration support for items, prices, and timing

## Requirements

- **Minecraft Version**: 1.20.6+
- **Java Version**: 17+
- **Required Plugins**: 
  - Vault (for economy integration)
  - An economy plugin (e.g., EssentialsX, CMI, etc.)

## Installation

1. Place the `nomad.jar` file in your server's `/plugins` directory
2. Restart or reload the server
3. Configure the `config.yml` file as needed
4. Reload the plugin with `/nomad reload`

## Configuration

### Basic Settings

```yaml
# Menu appearance
menu-title: "&6Nomad Merchant"
menu-rows: 3

# Daily item selection
daily-item-count: 5

# Refresh schedule
refresh-hour: 18
refresh-interval-minutes: 1440
```

### Item Pool Configuration

Add items to the shop pool in `config.yml`:

```yaml
item-pool:
  - material: DIAMOND
    name: "&bSpecial Diamond"
    price: 100.0
  - material: GOLDEN_APPLE
    name: "&6Golden Apple"
    price: 25.0
```

**Item Properties:**
- `material`: Valid Bukkit Material name
- `name`: Display name with color codes (&)
- `price`: Cost in economy currency

## Commands

### Player Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/nomad` | `nomad.use` | Shows help menu |
| `/nomad shop` | `nomad.use` | Opens merchant menu |
| `/nomad when` | `nomad.when` | Shows time until next refresh |
| `/nomad help` | `nomad.player.info` | Shows help menu |

### Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/nomad reload` | `nomad.reload` | Reloads configuration |
| `/nomad refresh` | `nomad.refresh` | Forces daily item refresh |
| `/nomad settime <minutes>` | `nomad.settime` | Changes refresh interval |
| `/nomad pool` | `nomad.pool` | Shows item pool |
| `/nomad pool add <material> <name> <price>` | `nomad.pool` | Adds item to pool |
| `/nomad pool remove <number>` | `nomad.pool` | Removes item from pool |

## Permissions

### Basic Permissions

- `nomad.use`: Access to basic commands (default: true)
- `nomad.when`: View refresh timer (default: true)
- `nomad.player.info`: View player help (default: true)

### Admin Permissions

- `nomad.admin`: All admin permissions (default: op)
- `nomad.admin.info`: View admin help (default: op)
- `nomad.reload`: Reload configuration (default: op)
- `nomad.refresh`: Force item refresh (default: op)
- `nomad.settime`: Change refresh timing (default: op)
- `nomad.pool`: Manage item pool (default: op)

## Usage Examples

### Adding Items to Pool

```
/nomad pool add DIAMOND_SWORD &cSharp Sword 150.0
/nomad pool add ENCHANTED_BOOK &aMagic Book 75.0
```

### Managing Refresh Schedule

```
/nomad settime 720    # Refresh every 12 hours
/nomad settime 60     # Refresh every hour
```

### Viewing Information

```
/nomad when           # Shows time until next refresh
/nomad pool           # Lists all items in the pool
```

## Automatic Features

### Daily Refresh System

- Items automatically refresh at the configured hour (default: 18:00)
- Random selection from the item pool
- Persistent storage of current daily items
- Automatic cleanup and reset

### Economy Integration

- Seamless Vault integration
- Automatic balance checking
- Transaction logging
- Error handling for economy issues

## Troubleshooting

### Common Issues

1. **Economy Not Working**
   - Ensure Vault is installed
   - Verify economy plugin is active
   - Check player permissions

2. **Items Not Refreshing**
   - Check refresh time configuration
   - Verify scheduler is running
   - Use `/nomad refresh` to force refresh

3. **Permission Errors**
   - Verify permissions in plugin.yml
   - Check permission plugin configuration
   - Ensure proper inheritance

### Debug Mode

Enable debug mode in config.yml:

```yaml
debug: true
```

This will provide additional logging information in the console.

## API Integration

The plugin integrates with:
- **Vault API**: For economy transactions
- **Bukkit API**: For inventory management and events

## Version History

- **v1.1.1**: Current stable version
- **v1.1.0**: Added admin management commands
- **v1.0.0**: Initial release with basic merchant system

## Support

For issues and support:
- Check console logs for error messages
- Verify all requirements are met
- Ensure proper configuration format

## Developer Notes

- Plugin uses Bukkit scheduler for automatic refresh
- Configuration is automatically saved and loaded
- Error handling prevents plugin crashes
- Multi-language support through configuration
