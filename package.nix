{
  lib,
  stdenv,
  fetchYarnDeps,
  yarnConfigHook,
  nodejs,
  electron,
  makeWrapper,
  copyDesktopItems,
  makeDesktopItem,
  proxy ? null
}:

let
  proxyFlags = lib.optionalString (proxy != null)
    (lib.concatMapStringsSep " "
      (var: "--set ${var} ${lib.escapeShellArg proxy}")
      [ "HTTP_PROXY" "HTTPS_PROXY" ]);
in

stdenv.mkDerivation (finalAttrs: {
  pname = "neuro-karaoke-wrapper";
  version = (lib.importJSON ./Desktop/package.json).version;

  src = ./Desktop;

  yarnOfflineCache = fetchYarnDeps {
    yarnLock = finalAttrs.src + "/yarn.lock";
    hash = "sha256-m9duRIrxX335bmGTWXGqncduf08d41zjxHZPGq2BtPE=";
  };

  nativeBuildInputs = [
    yarnConfigHook
    nodejs
    makeWrapper
    copyDesktopItems
  ];

  env.ELECTRON_SKIP_BINARY_DOWNLOAD = "1";

  buildPhase = ''
    runHook preBuild

    yarn --offline run build:pre

    cp -r ${electron.dist} electron-dist
    chmod -R u+w electron-dist

    yarn --offline run electron-builder --dir \
      -c.electronDist=electron-dist \
      -c.electronVersion=${electron.version} \
      -c.npmRebuild=false

    runHook postBuild
  '';

  installPhase = ''
    runHook preInstall

    mkdir -p $out/lib/neuro-karaoke-wrapper
    cp -r dist/linux-unpacked/resources/* $out/lib/neuro-karaoke-wrapper/

    makeWrapper ${lib.getExe electron} $out/bin/neuro-karaoke-wrapper \
      --add-flags $out/lib/neuro-karaoke-wrapper/app.asar \
      --add-flags "\''${NIXOS_OZONE_WL:+\''${WAYLAND_DISPLAY:+--ozone-platform-hint=auto --enable-features=WaylandWindowDecorations}}" \
      --inherit-argv0 \
      ${proxyFlags}

    install -Dm644 assets/neurokaraoke.png \
      $out/share/icons/hicolor/256x256/apps/neurokaraoke.png

    runHook postInstall
  '';

  desktopItems = [
    (makeDesktopItem {
      name = "neuro-karaoke-wrapper";
      desktopName = "Neuro Karaoke Player";
      comment = "A cross-platform karaoke player for neurokaraoke.com";
      exec = "neuro-karaoke-wrapper";
      icon = "neurokaraoke";
      categories = [ "AudioVideo" "Player" ];
      terminal = false;
    })
  ];

  meta = {
    description = "Desktop wrapper for neurokaraoke.com with media controls and tray support";
    homepage = "https://neurokaraoke.com";
    license = lib.licenses.gpl3;
    mainProgram = "neuro-karaoke-wrapper";
    platforms = lib.platforms.linux;
  };
})
