# Лаба №5 в VirtualBox: создаёт 3 host-only сети, DHCP для VMnet2 и 5 пустых ВМ (без ОС).
# Запуск (PowerShell, от администратора - нужно для создания сетевых адаптеров):
#   .\setup-lab5.ps1
#   .\setup-lab5.ps1 -VmDir "D:\VMs"
# Повторный запуск создаст дубликаты - сначала удалите ВМ и адаптеры (см. README.md, раздел "Сброс").
param(
    [string]$VmDir = "$HOME\VirtualBox VMs",
    [string]$VBox  = "VBoxManage"
)

# Ошибки VBoxManage ловим по $LASTEXITCODE (в Windows PowerShell 5.1 "Stop" ломает вызовы с 2>$null)
$ErrorActionPreference = "Continue"

function VB {
    & $VBox @args
    if ($LASTEXITCODE -ne 0) { throw "VBoxManage $($args -join ' ') -> код $LASTEXITCODE" }
}

# --- 1. Виртуальные сети --------------------------------------------------
# VMnet1 -> 192.168.40.0/24, VMnet2 -> 192.168.80.0/24, VMnet3 -> 192.168.120.0/24
# Хост (основная ОС) получает адрес .1, как в методичке.
$nets = [ordered]@{
    VMnet1 = "192.168.40"
    VMnet2 = "192.168.80"
    VMnet3 = "192.168.120"
}
$ifName = @{}
foreach ($n in $nets.Keys) {
    $out = (& $VBox hostonlyif create 2>&1 | Out-String)
    if ($out -notmatch "'(.+?)'") { throw "Не удалось создать адаптер для ${n}: $out" }
    $ifName[$n] = $Matches[1]
    VB hostonlyif ipconfig $ifName[$n] --ip "$($nets[$n]).1" --netmask 255.255.255.0
    Write-Host "$n -> $($ifName[$n]) ($($nets[$n]).0/24)"
}

# DHCP: только в VMnet2 (WIN2000 должен получить 192.168.80.128).
# В VMnet1 и VMnet3 DHCP по заданию отключён.
$dhcpArgs = @("--server-ip=192.168.80.254", "--netmask=255.255.255.0",
              "--lower-ip=192.168.80.128", "--upper-ip=192.168.80.253", "--enable")
& $VBox dhcpserver add "--interface=$($ifName['VMnet2'])" @dhcpArgs 2>$null
if ($LASTEXITCODE -ne 0) {   # VirtualBox 6.x использует --ifname
    VB dhcpserver add "--ifname=$($ifName['VMnet2'])" @dhcpArgs
}

# --- 2. Виртуальные машины ------------------------------------------------
function New-LabVM {
    param($Name, $OsType, $RamMb, $DiskMb, $NicType, $Nics)
    VB createvm --name $Name --ostype $OsType --register --basefolder $VmDir
    VB modifyvm $Name --memory $RamMb --vram 16 --boot1 dvd --boot2 disk --boot3 none --boot4 none
    # звуковая карта не нужна (в методичке её отключают); синтаксис зависит от версии VirtualBox
    & $VBox modifyvm $Name --audio-driver none 2>$null
    if ($LASTEXITCODE -ne 0) { & $VBox modifyvm $Name --audio none 2>$null }
    $i = 1
    foreach ($nic in $Nics) {
        VB modifyvm $Name "--nic$i" $nic.Mode "--nictype$i" $NicType "--cableconnected$i" on
        if ($nic.Mode -eq "hostonly") {
            VB modifyvm $Name "--hostonlyadapter$i" $ifName[$nic.Net]
        } elseif ($nic.Mode -eq "nat") {
            VB modifyvm $Name "--natnet$i" $nic.NatNet   # подсеть NAT = 192.168.32.0/24 ("VMnet8")
        }
        $i++
    }
    $disk = Join-Path (Join-Path $VmDir $Name) "$Name.vdi"
    VB createmedium disk --filename $disk --size $DiskMb --variant Standard   # динамический диск
    # IDE подходит всем гостям, включая Win98/Win2000: диск 0:0, привод CD/DVD 1:0
    VB storagectl $Name --name IDE --add ide
    VB storageattach $Name --storagectl IDE --port 0 --device 0 --type hdd --medium $disk
    VB storageattach $Name --storagectl IDE --port 1 --device 0 --type dvddrive --medium emptydrive
    Write-Host "ВМ $Name создана"
}

# Для Win98/Win2000 используется карта AMD PCnet (драйвер есть в самих ОС), для остальных - Intel e1000.
New-LabVM -Name WIN98   -OsType Win98        -RamMb 64   -DiskMb 512   -NicType Am79C973 -Nics @(@{Mode="hostonly"; Net="VMnet3"})
New-LabVM -Name WIN2000 -OsType Windows2000  -RamMb 128  -DiskMb 2048  -NicType Am79C973 -Nics @(@{Mode="hostonly"; Net="VMnet2"})
New-LabVM -Name LINUX   -OsType Debian_64    -RamMb 1024 -DiskMb 10240 -NicType 82540EM  -Nics @(@{Mode="hostonly"; Net="VMnet1"})
New-LabVM -Name FREEBSD -OsType FreeBSD_64   -RamMb 512  -DiskMb 5120  -NicType 82540EM  -Nics @(
    @{Mode="hostonly"; Net="VMnet1"}, @{Mode="hostonly"; Net="VMnet2"}, @{Mode="hostonly"; Net="VMnet3"})
New-LabVM -Name NETBSD  -OsType NetBSD_64    -RamMb 512  -DiskMb 3072  -NicType 82540EM  -Nics @(
    @{Mode="hostonly"; Net="VMnet1"}, @{Mode="nat"; NatNet="192.168.32.0/24"})

Write-Host "`nГотово. Дальше: подключить ISO и поставить ОС (README.md, шаг 3)."
VB list hostonlyifs | Select-String "Name:|IPAddress:"
