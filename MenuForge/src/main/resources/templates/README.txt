MenuForge quick start
=====================

Folders
  menus/      one YAML per menu (edited by hand, auto-reloads) or via the web editor
  textures/   PNG inventory skins -> become font glyphs in the generated resource pack
  pack/       generated resource pack (served at http://<server>:<port>/pack.zip)
  data/       cooldowns

Web editor (no code):  run /mf editor  -> open the link, paste the token.
Open a menu:           /mf open <id>   or the menu's own command.
Skins in other plugins: /mf title <texture> -> click to copy, paste as the menu title.

Flow blocks (click actions) - map form or "[type] value" shorthand:
  message, broadcast, actionbar, title, sound, console, player, open, close, back, refresh,
  delay, give-money, give-item, reset-cooldown, teleport, set-var
Conditions (have a "deny:" branch that runs when the check fails):
  cooldown, permission, money, has-item, placeholder, chance, world, is-bedrock

Placeholders: %player_name% %player_uuid% %player_world% %player_x% %player_y% %player_z%
  %player_health% %player_food% %player_level% %player_gamemode% %player_ping% %online%
  %money% %cooldown_<key>% %arg_1%.. %args% %var_<name>% + PlaceholderAPI if installed.
