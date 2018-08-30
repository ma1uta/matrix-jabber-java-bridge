# Matrix-jabber-java-bridge (WIP)

It is a double-pupped bridge between the [Matrix](https://matrix.org) and the [XMPP](https://xmpp.org).

The bridge under development and has some restrictions and bugs.

# How it's works

The bridge works as the Application service (AS) for the matrix homeserver and as the External Component (XEP-0114).

The bridge maps the matrix clients to the xmpp users in the component's domain and maps the xmpp users
 to the Matrix users in the AS namespace.
Then it connects the room and the conference together and translate messages there and back again.

Matrix clients <=> Matrix homeserver <=> Bridge <=> Xmpp server <=> Xmpp clients.

For example, the Matrix user Alice (@alice:matrix.org) sends the message in the room. The homeserver sends
this message to the bridge. The bridge creates a new xmpp message with the text from the matrix message
and sends it to the connected conference. And place the corresponding puppet user (alice@mjjb.xmpp.server)
to the 'from' field.

# Requirements

- java 8
- the Matrix homeserver
- the Xmpp server
- postgresql 9.x and higher

# Compile the sources

For compile the sources you need:
- java jdk 8 (oracle or openjdk)
- apache maven 3.5.3 and higher

```
git clone https://github.com/ma1uta/matrix-jabber-java-bridge
cd matrix-jabber-java-bridge
mvn package
```

The compiled bridge will be in the target folder and has the name like `matrix-jabber-java-bridge-X.Y.Z.jar`
where X.Y.Z is the version of the bridge.

# Installation

1. It is recommends to run the bridge under the separated user.
Let's create it:
    ```
    useradd -m -d /srv/mjjb -s /bin/false mjjb
    ```
2. Place the bridge (downloaded from https://github.com/ma1uta/matrix-jabber-java-bridge/releases or compiled)
into the home folder.
3. Copy the configuration file mjjb.yml (you can find the example in the
 [config folder](https://github.com/ma1uta/matrix-jabber-java-bridge/tree/master/config)) to the home folder.
4. Copy the registration file registration file reg_mjjb.yml (you can find the example in the
 [config folder](https://github.com/ma1uta/matrix-jabber-java-bridge/tree/master/config))
to the any place where the Matrix homeserver can find it.
5. Create the postgresql database user and database for the bridge:
    ```
    create user mjjb password 'secret_db_password';
    create database mjjb owner mjjb;
    ```
6. Install the Matrix server (for example the [synapse](https://github.com/matrix-org/synapse)).
7. Install the Xmpp server (for example the [ejabberd](https://www.ejabberd.im/) or [prosody](http://prosody.im/))
8. Fill the reg_mjjb.yml with the correct values:
    - url - the `url` where the bridge will be deployed.
    - as_token - the token to authorize the bridge on the matrix homeserver.
    - hs_token - the token to authorize the matrix homeserver on the bridge.
9. Register the bridge on the xmpp server. For example for the ejabberd the registration section will look:
    ```
    listen:
      -
        port: 5223
        module: ejabberd_service
        hosts:
          "mjjb.xmpp.server.tld":
            password: "secret_xmpp_password"
    ```
10. Register the bridge on the matrix homeserver. Add the registration file in the homeserver.yml:
    ```
    app_service_config_files:
      - "/etc/matrix-synapse/bot.yaml"
    ```

# Master bot



# Current limitations

- the bridge works only with `m.text` events. Supports other message events will be come.
- the xmpp's master bot don't have any commands and don't respond on invitations.

# Future plans:

- fix bugs
- add support of the all message events.
- add support of the 1:1 conversations.
- add command or the xmpp master bot. 
- add opportunity to create the bridge not only from the matrix but from the xmpp conference too.

# Known bugs

When the bridge leave the conference or shutdown the puppet users in the xmpp conference won't leave.

Workaround: kick one of this puppet user and other users will be leave.

