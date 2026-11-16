{
  description = "Neuro Karaoke Wrapper";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }: let
    systems = [ "x86_64-linux" "aarch64-linux" ];
    forAllSystems = f: nixpkgs.lib.genAttrs systems (system: f nixpkgs.legacyPackages.${system});
  in {
    packages = forAllSystems (pkgs: rec {
      neuro-karaoke-wrapper = pkgs.callPackage ./package.nix { };
      default = neuro-karaoke-wrapper;
    });

    overlays.default = final: _prev: {
      neuro-karaoke-wrapper = final.callPackage ./package.nix { };
    };
  };
}
