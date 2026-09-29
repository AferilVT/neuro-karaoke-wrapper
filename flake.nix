{
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  outputs = { self, nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; };
      version = (builtins.fromJSON (builtins.readFile ./Desktop/package.json)).version;
      runtimeLibs = with pkgs; [
        libGL libgbm nss nspr atk at-spi2-atk cups dbus expat
        libdrm libxkbcommon mesa systemd alsa-lib fontconfig freetype
      ];
    in {
      packages.${system}.default = pkgs.stdenv.mkDerivation (finalAttrs: {
        pname = "neuro-karaoke-wrapper";
        version = version;
        src = ./Desktop;

        yarnOfflineCache = pkgs.fetchYarnDeps {
          yarnLock = finalAttrs.src + "/yarn.lock";
          hash = "sha256-m9duRIrxX335bmGTWXGqncduf08d41zjxHZPGq2BtPE=";
        };

        nativeBuildInputs = with pkgs; [
          nodejs
          yarn
          yarnConfigHook
          yarnBuildHook
          yarnInstallHook
          python3
          node-gyp
          electron
          makeWrapper
          copyDesktopItems
        ];
      
        env = {
          ELECTRON_SKIP_BINARY_DOWNLOAD = "1";
          npm_config_nodedir = pkgs.electron.headers;
          npm_config_runtime = "electron";
          npm_config_target = pkgs.electron.version;
          ELECTRON_OVERRIDE_DIST_PATH = "${pkgs.electron}/libexec/electron";
        };

        buildPhase = ''
          runHook preBuild
          yarn --offline run build:pre

          cp -r ${pkgs.electron.dist} electron-dist
          chmod -R u+w electron-dist

          yarn --offline run electron-builder --dir \
            -c.electronDist=electron-dist \
            -c.electronVersion=${pkgs.electron.version}

          runHook postBuild
        '';
        
        desktopItems = [
          (pkgs.makeDesktopItem {
            name = "neuro-karaoke-wrapper";
            desktopName = "Neuro Karaoke Player";
            comment = "A cross-platform karaoke player for neurokaraoke.com";
            exec = "neuro-karaoke-wrapper";
            icon = "neurokaraoke";
            categories = [ "AudioVideo" "Player" ];
            terminal = false;
          })
        ];

        installPhase = ''
          runHook preInstall

          mkdir -p $out/lib/neuro-karaoke-wrapper
          cp -r dist/linux-unpacked/resources/* $out/lib/neuro-karaoke-wrapper/

          mkdir -p $out/bin
          makeWrapper ${pkgs.electron}/bin/electron $out/bin/neuro-karaoke-wrapper \
            --add-flags "$out/lib/neuro-karaoke-wrapper/app.asar" \
            --add-flags "--no-sandbox" \
            --prefix LD_LIBRARY_PATH : "${pkgs.lib.makeLibraryPath runtimeLibs}"

          install -Dm644 ${./assets/neurokaraoke.png} $out/share/icons/hicolor/256x256/apps/neurokaraoke.png

          runHook postInstall
        '';

        meta = with pkgs.lib; {
          description = "A cross-platform karaoke player for neurokaraoke.com";
          license = licenses.gpl3;
          mainProgram = "neuro-karaoke-wrapper";
        };
      });

      nixosModules.default = { pkgs, ... }: {
        environment.systemPackages = [
          self.packages.${pkgs.stdenv.hostPlatform.system}.default
        ];
      };
    };
}
