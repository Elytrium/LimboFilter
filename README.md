<img src="https://elytrium.net/src/img/elytrium.webp" alt="Elytrium" align="right">

# LimboFilter

[![Join our Discord](https://img.shields.io/discord/775778822334709780.svg?logo=discord&label=Discord)](https://ely.su/discord)
[![Proxy Stats](https://img.shields.io/bstats/servers/12530?logo=minecraft&label=Servers)](https://bstats.org/plugin/velocity/LimboAPI/12530)
[![Proxy Stats](https://img.shields.io/bstats/players/12530?logo=minecraft&label=Players)](https://bstats.org/plugin/velocity/LimboAPI/12530)

Most powerful bot filtering solution for Minecraft proxies. Built with LimboAPI. \
[MC-Market](https://www.mc-market.org/resources/21097/) \
[SpigotMC.org](https://www.spigotmc.org/resources/limboapi-limboauth-limbofilter.95748/) \
[Описание и обсуждение на русском языке (spigotmc.ru)](https://spigotmc.ru/resources/limboapi-limboauth-limbofilter-virtualnye-servera-dlja-velocity.715/) \
[Описание и обсуждение на русском языке (rubukkit.org)](http://rubukkit.org/threads/limboapi-limboauth-limbofilter-virtualnye-servera-dlja-velocity.177904/)

Test server: [``ely.su``](https://hotmc.ru/minecraft-server-203216)

## See also

- [LimboAuth](https://github.com/Elytrium/LimboAuth) - Auth System built in virtual server (Limbo). Uses BCrypt, has TOTP 2FA feature. Supports literally any database due to OrmLite.
- [LimboAPI](https://github.com/Elytrium/LimboAPI) - Library for sending players to virtual servers (called limbo)

## Features of LimboFilter

- Improved Falling Check - Check X, Z coordinates, 
- Highly customizable CAPTCHA's - change fonts, backplates, colors, alphabet-pattern, length
- Client settings and brand checking
- Highly customisable config - you can change all the messages the plugin sends
- Ability to partially disable protection on low-loads of proxy
- Ability to prepare raw packets to reduce CPU usage
- Fast MCEdit schematic world loading for CAPTCHA checking
- And more..

### LimboFilter /vs/ popular antibot solutions:

Test server: i7-3770 (4c/8t 3.4GHz) Dedicated server, Ubuntu Server 20.04, OpenJDK 11, 16GB DDR3 1600MHz RAM, 4GB RAM is allocated to proxy. <br>
Attack: Motd + Join bot attack (100k joins per seconds, 1.17 Protocol)

Proxy server | Info | Boot time | % CPU on attack
--- | --- | --- | ---
Velocity | LimboFilter + LimboAuth Online/Offline Mode | 2 sec | 20%
Velocity | LimboFilter + Offline Mode | 2 sec | 20%
Leymooo's BungeeCord BotFilter | JPremium Online/Offline Mode | 8 sec | 95%
Leymooo's BungeeCord BotFilter | Offline Mode | 8 sec | 40%
yooniks' BungeeCord Aegis Escanor 1.3.1 | Offline Mode | 10 sec | 20%
yooniks' BungeeCord Aegis 9.2.1 | Offline Mode | 10 sec | 100% (what?)
Velocity | JPremium Online/Offline Mode | 2 sec | 95%
Velocity | Online Mode | 2 sec | 70%
Velocity | Offline Mode | 2 sec | 55%

## Donation

Your donations are really appreciated. Donations wallets/links/cards:

- MasterCard Debit Card (Tinkoff Bank): ``5536 9140 0599 1975``
- Qiwi Wallet: ``PFORG`` or [this link](https://my.qiwi.com/form/Petr-YSpyiLt9c6)
- YooMoney Wallet: ``4100 1721 8467 044`` or [this link](https://yoomoney.ru/quickpay/shop-widget?writer=seller&targets=Donation&targets-hint=&default-sum=&button-text=11&payment-type-choice=on&mobile-payment-type-choice=on&hint=&successURL=&quickpay=shop&account=410017218467044)
- PayPal: ``ogurec332@mail.ru``
