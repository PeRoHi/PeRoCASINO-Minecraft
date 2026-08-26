# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PeRoCasino — Paper/Spigot 1.21.x 向け Minecraft カジノプラグイン（`me.bokan.perocasino`）。

主な機能:

- 経済: 財布・借金・HUD / ローン利息
- ゲーム: ルーレット（ItemDisplay + 54枠ベット GUI）、ブラックジャック、High & Low、チンチロ卓、GUI スロット、ワールド設置スロット
- 拠点: 採石場リスポーン、ネザーポータル転送、コマンド集 / ルールブック、コマンド杖

設計・コマンドの正は `docs/COMMANDS.md` と `docs/RULEBOOK.md`。実装と文書が矛盾する場合は独断で片方を正にせず確認する。

## Build

```bash
mvn -q -DskipTests package
```

成果物: `target/PeRoCasino-*.jar`

開発用 Paper サーバー: `scripts/dev-server.sh`（JDWP 5005）。VS Code 設定は `.vscode/`。
