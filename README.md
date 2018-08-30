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
and sends it to the connected conference. And place the corresponding puppet user (alice@mjjb.xmpp.server/alice)
to the 'from' field of the xmpp message.

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
9. Add SRV DNS record of the bridge xmpp domain. For example, add the following SRV record:
    `_xmpp-server._tcp.mjjb.xmppserver.tld TTL IN SRV 10 10 5269 xmppserver.tld`
10. Register the bridge on the xmpp server. For example for the ejabberd the registration section will look:
    ```
    listen:
      -
        port: 5223
        module: ejabberd_service
        hosts:
          "mjjb.xmpp.server.tld":
            password: "secret_xmpp_password"
    ```
11. Register the bridge on the matrix homeserver. Add the registration file in the homeserver.yml:
    ```
    app_service_config_files:
      - "/etc/matrix-synapse/reg_mjjb.yml"
    ```
12. Fill the mjjb.yml with correct values:
    ```
    xmpp:
      componentName: 'mjjb.xmpp.server.tld'
      sharedSecret: 'secret_xmpp_password'
      hostname: 'xmpp.server.tld'
      port: 5223
    matrix:
      homeserver: 'https://matrix.homeserver.tld:8448'
      asToken: 'as_token'
      hsToken: 'ms_token'
      masterUserId: '@_xmpp_master:localhost'
    ```
    Specify the database url, username and password:
    ```
    database:
      user: mjjb
      password: secret_db_password
      url: jdbc:postgresql://postgresql/mjjb
    ```
13. In order to receive events from the matrix homeserver you should to specify the valid certificate.
    For example, you can use certificates from [Let's encrypt](https://letsencrypt.org/).
    The bridge can works with certificates in the PKCS12 format.
    To convert certificated from the pem format you can use next command:
    ```
    openssl pkcs12 -export -inkey privkey.pem -in fullchain.pem -out mjjb.pkcs12 
    ```
    And invoke the keystore password.
    Then correct the parameters in the configuration:
    ```
    server:
      applicationConnectors:
        - type: https
          port: 8444 # specify the AS port
          keyStorePath: /home/mjjb/mjjb.pkcs12 # path to the certificates
          keyStorePassword: secretCertPassword # password of the key store.
    ```
14. If you use this [file](https://github.com/ma1uta/matrix-jabber-java-bridge/blob/master/config/mjjb.service)
    for creating the systemd service.
15. Check the configuration:
    ```
    java -jar mjjb.jar check mjjb.yml
    ```
    
    Check the database status:
    ```
    java -jar mjjb.jar db status mjjb.yml
    ```
    
    Upgrade the database schema:
    ```
    java -jar mjjb.jar db migrate mjjb.yml
    ```
    
16. Start the bridge manually:
    ```
    java -jar mjjb.jar server mjjb.yml
    
    ```
    Or through systemd:
    ```
    systemctl daemon-reload
    systemctl start mjjb.service
    ```
   
# Master bot

The bridge besides the puppet users join the bot which called "master bot". This is done for the following
tasks:
- indicate that the bridge works in this room;
- invoke some command (create/destroy bridge, show info);
- kick the master bot will destroy the bridge in the room from where the master bot was kicked.

In order to connect the room with the conference you can add the special room alias `#_xmpp_<name>_<server>:homeserver.tld`
where the `<name>` is a conference name and the `<server>` is a xmpp server. For example, to connect with
the conference bridge@conference.jabber.org the alias will be look as `#_xmpp_bridge_conference.jabber.org:matrix.server.org`.
But users can add room aliases only from they domain. Therefore users can invite the master bot and tell him
to add this alias to the current room.

### Master bot commands:
- `connect <conference url>` - connect current room with the conference specified in `<conference url>`.
- `disconnect` - destroy the bridge.
- `info` - show summary of the bridge (room id, room alias, conference url).
- `members` - show the pupper users.

# Current limitations

- the bridge works only with `m.text` events. Supports other message events will be come.
- the xmpp's master bot don't have any commands and don't respond on invitations.

# Future plans:

- fix bugs
- add support of the all message events.
- add support of the 1:1 conversations.
- add command or the xmpp master bot. 
- add opportunity to create the bridge not only from the matrix but from the xmpp conference too.
- add maintenance commands which allow to create, stop and check transports and state of the bridge.
- implement the protocol api.

# Known bugs

When the bridge leave the conference or shutdown the puppet users in the xmpp conference won't leave.

Workaround: kick one of this puppet user and other users will be leave.

