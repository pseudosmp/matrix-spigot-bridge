# [MatrixSpigotBridge (fork) on Spigot](https://www.spigotmc.org/resources/matrixspigotbridge-fork.125450/)

MatrixSpigotBridge is a Spigot plugin that uses [matrix.org](https://matrix.org), an instant messaging protocol, to let interact with the players without logging onto the Minecraft server.

This is a fork of MatrixSpigotBridge that aims to continue support and add new features. Currently, this fork adds these features:
- Matrix room commands: <br><br>![image](https://github.com/user-attachments/assets/ecdb0cf0-ab6a-4368-8216-fe377826b66c)<br><br>
- Asynchronous connection to Matrix Server, so that your server isnt prevented from starting if the plugin is unable to connect.
- In-game commands: `/msb [ping | reload | restart]`

This will let players the ability to chat with people on your Matrix room as well as having people on the Matrix room be able to chat with people on the Minecraft server.

The plugin could be useful for example in the situation of someone not being at their computer and wanting to talk to in-game players.

[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245 'Plugin PlaceholderAPI by clip') is supported so if you have it, you can add fancy placeholders in all plugin's messages :)

Support and Feature Requests in [SpigotMC Resource Discussion Tab](https://www.spigotmc.org/threads/matrixspigotbridge-fork.691428/) / [Discord](https://dsc.gg/pseudoforceyt) only! Do NOT use the issues tab for this.

Building:
1. Clone the project (the version/branch of your choice)
2. Build using `mvn install`

[bStats - MatrixSpigotBridge](https://bstats.org/plugin/bukkit/MatrixSpigotBridge/25993)
![image](https://bstats.org/signatures/bukkit/MatrixSpigotBridge.svg)
