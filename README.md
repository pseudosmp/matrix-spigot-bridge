# MatrixSpigotBridge v2

![SpigotMC Downloads](https://img.shields.io/spiget/downloads/125450?style=for-the-badge&logo=spigotmc&link=https%3A%2F%2Fwww.spigotmc.org%2Fresources%2Fmatrixspigotbridge-v2.125450)&ensp;![Modrinth Downloads](https://img.shields.io/modrinth/dt/matrixspigotbridge-v2?style=for-the-badge&logo=modrinth&link=https%3A%2F%2Fmodrinth.com%2Fplugin%2Fmatrixspigotbridge-v2)

MatrixSpigotBridge is a Spigot plugin that uses [matrix.org](https://matrix.org), an instant messaging protocol, to let interact with the players without logging onto the Minecraft server.

This is a fork of MatrixSpigotBridge that aims to continue support and add new features. Currently, this fork adds these features:

- Asynchronous connection to Matrix Server, so that your server isnt prevented from starting if the plugin is unable to connect.
- Format messages in config with HTML tags
- Matrix room commands: `<br><br>`![image](https://github.com/user-attachments/assets/be26ce75-6ff6-422a-b4be-78be042ab6e5)`<br><br>`
- In-game commands: `/msb [ping | reload | restart]` `<br><br>`![image](https://github.com/user-attachments/assets/cbbe5b2e-d171-4bab-8edc-e7e4dce20179)`<br><br>`
- Reserialization (preserves formatting) across the two chats`<br><br>`![image](https://github.com/user-attachments/assets/b8d8a914-1e6f-43da-866b-3048e0f736e1)`<br><br>`

[PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245 "Plugin PlaceholderAPI by clip") is supported so if you have it, you can add fancy placeholders in all plugin's messages :D Here is an example usage

Config:

```yaml
format:
    player:
        join: '➕ {MESSAGE}'
        quit: '➖ {MESSAGE}'
        death: '🪦 {MESSAGE}'
        chat: <blockquote><b>{PLAYERNAME}</b> from %playerbiomes_biome_name_english%</blockquote>{MESSAGE}
```

Matrix Chat:

![image](https://github.com/user-attachments/assets/e12db434-07e5-44b6-9039-56e3b9ecca5d)

(pssst, check out PlayerBiomes [here](https://github.com/pseudosmp/PlayerBiomes))

Support and Feature Requests in [SpigotMC Resource Discussion Tab](https://www.spigotmc.org/threads/matrixspigotbridge-fork.691428/) / [Discord](https://dsc.gg/pseudoforceyt) only! Do NOT use the issues tab for this.

**Prerequisites**

* A Matrix room (can be on any homeserver)
* A Matrix user (AKA the "Bot") that can read/write in that room

**Installation**

1. Put the plugin in your plugins directory
2. Edit the generated config.yml file to fit your needs (shown below for reference)
3. Reboot your server and that's all!

**Configuration**

In the example configuration, we have a user *@mcbot:example.com* whose password is *y0urPa55w0rd* (the user is registered at home server  *example.com:8448* ) we want to bind it the room* !fdsgfdKJHGKujys:example.com* to the minecraft chat

*Optionally:* If you have issues with the bot Authentication, or simply don't want to store the password cleartext at all, you can force the bot account's authentication key into *access.yml.* You can find this at: *Settings > Help & About > Advanced > Access token* (Element client) or similar in your preferred client.

**Security tip:** Once the bot user have successfully connected at least once, an access token will be generated in access.yml to avoid using password anymore, so you can safely set the *password * to empty value if you don't want password stored in clear text in your config file.

Building:

1. Clone the project (the version/branch of your choice)
2. Build using `mvn install`
