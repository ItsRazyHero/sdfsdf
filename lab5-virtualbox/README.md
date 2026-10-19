# Лаба №5 «Виртуальное макетирование компьютерных сетей» — в VirtualBox

Методичка написана под VMware Workstation 5.5 (2006 г.). Здесь та же сеть, перенесённая на VirtualBox 7.x.

## 0. Что чему соответствует

| VMware (методичка)            | VirtualBox                                                         |
|-------------------------------|--------------------------------------------------------------------|
| VMnet1 (host-only, 192.168.40.0) | Host-only адаптер #1, хост = 192.168.40.1, DHCP выключен        |
| VMnet2 (host-only, 192.168.80.0) | Host-only адаптер #2, хост = 192.168.80.1, DHCP .128–.253       |
| VMnet3 (host-only, 192.168.120.0)| Host-only адаптер #3, хост = 192.168.120.1, DHCP выключен       |
| VMnet8 (NAT, 192.168.32.0)    | Сетевая карта типа **NAT** у ВМ NETBSD с подсетью 192.168.32.0/24  |
| Virtual Network Editor        | File → Tools → Network Manager (+ `VBoxManage`)                    |
| VMware Tools                  | Guest Additions (для Win98 их нет — это нормально)                 |
| Лимит 3 сетевых карты         | В VirtualBox до 4 в GUI — ограничение из методички снимается       |

Схема (как в методичке):

```
                 Интернет / реальная сеть
                          │
                   NAT 192.168.32.2
                          │
                 NETBSD (шлюз)  wm1: 192.168.32.15*   ← см. примечание про .128
                          │ wm0: 192.168.40.57
 ┌────────────────────────┴──────────── VMnet1 192.168.40.0/24 ─────┐
 │ LINUX 192.168.40.32 (gw .2)                                       │
 └────────────────────────┬──────────────────────────────────────────┘
                          │ .2
                   FREEBSD (маршрутизатор)  .2 / .2 / .2
              ┌───────────┴────────────┐
   VMnet2 192.168.80.0/24      VMnet3 192.168.120.0/24
   WIN2000 (DHCP → .128)       WIN98 192.168.120.15 (gw .2)
```

\* В VirtualBox встроенный NAT выдаёт гостю **192.168.32.15** (шлюз/NAT-устройство — 192.168.32.2), а не .128 как у VMware. Это единственное
расхождение по адресам; в отчёте так и напишите.

## 1. Что нужно скачать

- VirtualBox 7.x (+ Extension Pack не нужен).
- Образы ОС. Оригинальные Mandrake 2006 / FreeBSD 6 / NetBSD 3.0 не стоит искать — берите современные аналоги, роли те же:

  | ВМ      | ОС в методичке  | Что ставить                                   |
  |---------|-----------------|-----------------------------------------------|
  | WIN98   | Windows 98      | Windows 98 SE (iso/дискета загрузки) — как есть |
  | WIN2000 | Windows 2k/XP   | Windows 2000 или XP                           |
  | LINUX   | Linux Mandrake  | Debian / Ubuntu Server (нужен Web-сервер: apache2/nginx) |
  | FREEBSD | FreeBSD         | FreeBSD 13/14 (`disc1.iso`)                   |
  | NETBSD  | NetBSD 3.0      | NetBSD 10 (`amd64cd.iso`)                     |

Если преподаватель требует именно старые версии — схема не меняется, только имена интерфейсов (`lnc0` вместо `em0` и т. п.).

## 2. Создать сети и ВМ

Автоматически (рекомендуется): PowerShell **от администратора**:

```powershell
cd lab5-virtualbox
.\setup-lab5.ps1 -VmDir "D:\VMs"      # путь по желанию
```

Скрипт: создаёт 3 host-only адаптера с адресами хоста `.1`, включает DHCP только в VMnet2 (`.128–.253`, сервер `.254`),
создаёт ВМ WIN98, WIN2000, LINUX, FREEBSD, NETBSD с нужным числом карт и привязкой к сетям. ОС он не ставит.

> Скрипт не проверялся на вашей машине. Если какая-то команда упала — он остановится и покажет какая; это можно доделать руками по таблице выше.

**Вручную (GUI), если нужно для отчёта со скриншотами:**
1. File → Tools → Network Manager → Host-only Networks → Create ×3. Для каждой: IPv4 Address `192.168.X.1`, Mask `255.255.255.0`,
   DHCP Server — включить только у сети 192.168.80.0 (Lower `192.168.80.128`, Upper `192.168.80.253`, Server `192.168.80.254`).
2. Machine → New. RAM/диск — как в таблице ниже. Settings → Network: для каждой карты Attached to = *Host-only Adapter* и нужный адаптер.
   У NETBSD вторая карта — *NAT* (Advanced → Port Forwarding, см. шаг 5).

| ВМ      | RAM    | Диск (динамический) | Карты |
|---------|--------|---------------------|-------|
| WIN98   | 64 МБ  | 0,5 ГБ              | VMnet3 (тип AMD PCnet-FAST III) |
| WIN2000 | 128 МБ | 2 ГБ                | VMnet2 (AMD PCnet-FAST III) |
| LINUX   | 1 ГБ   | 10 ГБ               | VMnet1 |
| FREEBSD | 512 МБ | 5 ГБ                | VMnet1, VMnet2, VMnet3 |
| NETBSD  | 512 МБ | 3 ГБ                | VMnet1, NAT |

> **Linux/macOS-хост:** VirtualBox по умолчанию разрешает host-only только в 192.168.56.0/21. Создайте `/etc/vbox/networks.conf` со строкой
> `* 192.168.0.0/16`, иначе адреса 40/80/120 будут отклонены. На Windows ограничения нет.

## 3. Установка гостевых ОС

Подключите ISO: Settings → Storage → пустой привод → Choose a disk file. Загрузка идёт с CD (в скрипте порядок загрузки уже dvd → disk).

- **WIN98**: загрузиться с загрузочного диска, `fdisk` → основной раздел, активный → перезагрузка → `format c:` → установка. Драйвер сетевой карты
  «AMD PCNET Family» находится автоматически. Guest Additions для Win98 не ставятся — разрешение увеличивается драйвером VBEMouse/SciTech
  (в методичке аналог — установка видеодрайвера из VMware Tools; можно оставить 640×480/16 цветов и отметить это в отчёте).
- **WIN2000 / XP**: обычная установка; после неё Devices → Insert Guest Additions CD → установить (необязательно).
- **LINUX**: в инсталляторе выбрать сервер (SSH + web-сервер). Сеть пока не трогайте — настроите в шаге 4.
- **FREEBSD / NETBSD**: обычный текстовый инсталлятор; при вопросе о сети можно пропустить — настроим руками.

После установки: Settings → извлечь ISO. Не используемые в данный момент ВМ — Machine → Close → Save State (аналог Suspend).

## 4. Настройка TCP/IP

### WIN98 (VMnet3, статика)
Панель управления → Сеть → TCP/IP:
- IP `192.168.120.15`, маска `255.255.255.0`, шлюз `192.168.120.2`.

### WIN2000 (VMnet2, DHCP)
Свойства подключения → TCP/IP → *Получить IP-адрес автоматически*. Должен получить **192.168.80.128** (проверка: `ipconfig /all`).
Шлюз по умолчанию задайте вручную: `192.168.80.2` (встроенный DHCP-сервер VirtualBox может выдать другой шлюз — проверьте `route print`).

### LINUX (VMnet1, статика) — Debian/Ubuntu с ifupdown
`/etc/network/interfaces`:
```
auto lo
iface lo inet loopback

auto eth0
iface eth0 inet static
    address 192.168.40.32
    netmask 255.255.255.0
    gateway 192.168.40.2
```
Если используется netplan — адрес `192.168.40.32/24`, `routes: [{to: default, via: 192.168.40.2}]`. Имя интерфейса смотрите в `ip a` (может быть `enp0s3`).

Ручной вариант из методички (до перезагрузки), если нужно показать:
```
ifconfig lo 127.0.0.1
ifconfig eth0 192.168.40.32 netmask 255.255.255.0 up
route add -net 192.168.40.0 netmask 255.255.255.0 eth0
route add default gw 192.168.40.2 eth0
```

### FREEBSD — маршрутизатор (VMnet1/2/3)
`/etc/rc.conf` (интерфейсы `em0/em1/em2` — порядок карт в VirtualBox: 1→VMnet1, 2→VMnet2, 3→VMnet3):
```
gateway_enable="YES"
defaultrouter="192.168.40.57"
ifconfig_em0="inet 192.168.40.2 netmask 255.255.255.0"
ifconfig_em1="inet 192.168.80.2 netmask 255.255.255.0"
ifconfig_em2="inet 192.168.120.2 netmask 255.255.255.0"
```
> В методичке опечатка: маска `255.255.225.0` — правильно **255.255.255.0**.

`reboot`.

### NETBSD — шлюз (VMnet1 + NAT)
Интерфейсы: `wm0` — VMnet1, `wm1` — NAT. `/etc/rc.conf`:
```
ifconfig_wm0="inet 192.168.40.57 netmask 255.255.255.0"
dhcpcd=YES
dhcpcd_flags="-4 wm1"
```
`/etc/sysctl.conf`:
```
net.inet.ip.forwarding=1
```
Шлюз по умолчанию (192.168.32.2) прилетит по DHCP от встроенного NAT VirtualBox.

**Чего нет в методичке, но без этого связь не заработает:**
1. Шлюзу нужны маршруты в «дальние» подсети через маршрутизатор:
   ```
   route add -net 192.168.80.0/24 192.168.40.2
   route add -net 192.168.120.0/24 192.168.40.2
   ```
   (на постоянку — через `static_routes` в `rc.conf`, см. `man rc.conf`, или добавьте в `/etc/rc.local`).
2. Шлюз должен подменять адреса внутренних сетей на свой «внешний» (маскарад), иначе встроенный NAT VirtualBox не будет надёжно пропускать
   пакеты с чужими адресами (особенно ping). Пример `/etc/npf.conf` (проверьте синтаксис по `man npf.conf` для вашей версии NetBSD):
   ```
   $ext_if = "wm1"
   $int_if = "wm0"

   alg "icmp"
   map $ext_if dynamic 192.168.0.0/16 -> ifaddrs($ext_if)

   group "external" on $ext_if {
       pass stateful out final all
       block in final all
   }
   group "internal" on $int_if {
       pass final all
   }
   group default {
       pass final on lo0 all
       block all
   }
   ```
   В `rc.conf`: `npf=YES`, затем `service npf reload`.

## 5. Доступ из внешней сети к веб-серверу LINUX (проброс порта 80)

В методичке NAT-устройство VMnet8 пробрасывает порт 80 на LINUX. В VirtualBox встроенный NAT видит только гостя NETBSD, поэтому проброс двойной:

1. **VirtualBox → NETBSD → Settings → Network → Adapter 2 → Advanced → Port Forwarding:**
   Name `web`, Protocol TCP, Host Port `8080` (порт 80 на Linux/macOS требует root), Guest IP `192.168.32.15`, Guest Port `80`.
2. **На NETBSD** перенаправить 80 на LINUX — в `npf.conf` добавить правило редиректа (синтаксис — `man npf.conf`, раздел NAT/`map … <-`):
   входящий TCP/80 на `wm1` → `192.168.40.32:80`.
3. Проверка с реальной машины: `http://localhost:8080/` → страница Web-сервера LINUX.

Если проброс не нужен вашему варианту — этот шаг пропускается.

## 6. Тестирование (этап 5)

Порядок: снизу вверх. Запустите все 5 ВМ.

| Откуда → куда                              | Команда                                             | Ожидается |
|--------------------------------------------|-----------------------------------------------------|-----------|
| хост → VMnet1/2/3 (сетевые адаптеры)       | `ping 192.168.40.2` / `.80.2` / `.120.2`            | ответ (через адаптеры `.1`) |
| WIN98 → маршрутизатор                      | `ping 192.168.120.2`                                | ответ |
| WIN98 → LINUX                              | `ping 192.168.40.32`, `tracert 192.168.40.32`       | 2 прыжка: .120.2 → .40.32 |
| WIN2000 → адрес по DHCP                    | `ipconfig /all`                                     | 192.168.80.128 |
| WIN2000 → WIN98                            | `ping 192.168.120.15`                               | ответ |
| LINUX → шлюз                               | `ping 192.168.40.57`                                | ответ |
| любой хост → внешний мир                   | `ping 8.8.8.8`, затем `ping ya.ru` / `tracert 8.8.8.8` | ответ, маршрут через .2 → .57 → 192.168.32.2 |
| внешний → LINUX Web                        | браузер: `http://localhost:8080/`                   | страница |

DNS для клиентов задайте руками (`8.8.8.8` или DNS-сервер LINUX), т. к. DHCP/NAT без него именами не обслужит.

Для отчёта: скриншоты Network Manager (адаптеры + DHCP), Settings → Network каждой ВМ, вывод `ipconfig`/`ifconfig` на каждом хосте,
`route print`/`netstat -rn`, результаты `ping`/`tracert` из таблицы.

## 7. Сброс (если нужно начать заново)

```powershell
foreach ($v in "WIN98","WIN2000","LINUX","FREEBSD","NETBSD") { VBoxManage unregistervm $v --delete }
VBoxManage list hostonlyifs                 # найти имена адаптеров 192.168.40/80/120
VBoxManage dhcpserver remove --interface="<имя>"      # для VMnet2 (в 6.x: --ifname)
VBoxManage hostonlyif remove "<имя>"
```
