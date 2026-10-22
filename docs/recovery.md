# Recovery

Read this before turning off the stock launcher.

## HOME shows a black screen or nothing happens

The stock launcher is off and the new Home isn't answering. adb still works.

```sh
sh scripts/undo.sh 192.168.1.50
```

That re-enables everything recorded in `/data/local/tmp/tvhome-disabled.txt` on
the TV and makes `com.google.android.tvlauncher` the Home again. If you want to
do only the launcher by hand:

```sh
adb -s 192.168.1.50:5555 shell pm enable com.google.android.tvlauncher
adb -s 192.168.1.50:5555 shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb -s 192.168.1.50:5555 shell input keyevent KEYCODE_HOME
```

## The new home screen crashes on open

```sh
adb -s 192.168.1.50:5555 logcat -d | grep -A20 'dev.lucas.tvhome'
```

Fix, rebuild, `install.sh` again. Until then, the commands above bring the stock
launcher back.

## adb stops answering

- The TV sleeps the network in standby on some settings. Wake it with the remote.
- `adb kill-server && adb connect <IP>`.
- Full mains power-off for ten seconds, then connect again shortly after boot.
- If USB debugging got turned off (a firmware update can do this), turn it on
  again from Settings on the TV; the new home screen has a Settings tile.

## Live TV, HDMI or the remote misbehaves after debloat

Something in the `[choice]` tier, or a package that your firmware uses
differently. `undo.sh` restores everything; then disable packages a few at a time
and test between rounds. Suspects first: `org.droidtv.amazonalexa` (live TV binds
to it on some builds), `org.droidtv.dlna`, `com.google.android.katniss`.

## Last resort

Factory reset from Settings → Android settings → Storage & reset. It wipes the
disabled list, the Home preference and the installed home screen, and returns
the TV to the Philips home. Your Google account and apps need setting up again.

## Never

- `adb reboot bootloader` or `fastboot` on this platform. Owners of TPM171E TVs
  report it can soft-brick the TV.
- Disabling anything in `[keep]`, in particular `org.droidtv.eum`,
  `org.droidtv.nettvbrowser`, `org.droidtv.playtv`, `org.droidtv.xtv`,
  `org.droidtv.GlobalKey`, `org.droidtv.tvsystemui`.
- `pm uninstall`. Everything here uses `pm disable-user`, which is reversible.
