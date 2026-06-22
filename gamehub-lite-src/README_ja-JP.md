# GameHub Lite

**Language:** [English](./README.md) | **日本語**

## GameHub Liteとは何ですか？

GameHub Liteは、教育目的のためにコミュニティによって維持と管理されているGameHubの改良版です。

---

## GameHub Lite Patcher

GameHub 5.1.0をGameHub Liteに変換するパッチ適用システムです。プライバシーを重視した軽量版でテレメトリによる情報収集機能の削除、オフラインサポートを追加しています。

## GameHub Liteは何が違うんですか？

GameHub Liteは、GameHubを改良したバージョンになります:

- **テレメトリとトラッキングを削除** - Umeng analytics、Firebase、JPush、クラッシュレポートを削除
- **ブロートを削除** - クラウドストリーミング、Xboxクラウドゲーミング、ソーシャル機能などを削除
- **アカウント要求を削除** - ログイン不要でアプリが使用できます
- **アプリのサイズを削減** - 118MBから52MBに削減 (-56%)
- **不要な権限を削除** - 位置情報、連絡先、電話の状態などの権限を削除
- **オフラインサポートを追加** - ネットワーク接続なしでも動作可能になっています
- **パッケージ名を変更** - サイドバイサイドインストール用に`gamehub.lite`に変更
- **カスタムニュースページを追加** - アップデートでコミュニティAPIを使用可能にします

## クイックスタート

### 前提条件

必要なツールをインストールしてください:

`adb`は、Android Studioまたは`platform-tools`パッケージから入手できます。

```bash
# macOS
brew install apktool openjdk

# Ubuntu/Debian
sudo apt install apktool openjdk-17-jdk

# Arch
sudo pacman -S apktool jdk17-openjdk

# Fedora (dnf):
sudo dnf install apktool java-17-openjdk

# Windows
stop using windows
# JK.
# apktoolを https://apktool.org/ ダウンロード
# OracleまたはAdoptOpenJDK 17以降をインストールしてください
# 未テストですが、WSL2でも動作するはずです

```

### パッチを適用

1. GameHub 5.1.0のAPKをダウンロードし、`apk/GameHub-5.1.0.apk`に配置

2. パッチャーを実行:

```bash
./patch.sh
```

3. 出力されたAPKをインストール:

```bash
adb install output/GameHub-Lite.apk
```

#### カスタムキーストアの使い方

デフォルトでは、パッチでデバッグ用のキーストアを生成しますが、環境変数を使用してキーストアの設定を上書きすることもできます:

```bash
KEYSTORE="./keystore/release.keystore" \
KEYSTORE_PASS="your_password" \
KEY_ALIAS="your_key_alias" \
./patch.sh
```

またはエクスポートしてください:

```bash
export KEYSTORE="./keystore/release.keystore"
export KEYSTORE_PASS="your_password"
export KEY_ALIAS="your_key_alias"
./patch.sh
```

キーエイリアスを見つけるには以下を実行:

```bash
keytool -list -keystore ./keystore/release.keystore
```

#### ビルドのリリースバリアント

GameHub Liteは、特定のデバイスのパフォーマンス最適化を可能にするために、複数のパッケージ名バリアントのリリースバージョンで配布しています。詳細は[GameHub Liteの異なるバージョン](#異なるバージョンについて)を参照してください。

5つのバリアントすべてを一度にビルドするには`RELEASE=true`フラグを使用します:

```bash
RELEASE=true \
KEYSTORE="./keystore/release.keystore" \
KEYSTORE_PASS="your_password" \
KEY_ALIAS="your_key_alias" \
./patch.sh
```

`output/`ディレクトリに以下のAPKファイルが生成されます:

| バリアント    | ファイル名                                 | パッケージ名                |
| ---------- | ---------------------------------------- | --------------------------- |
| Base       | `GameHub-Lite-v{バージョン}.apk`            | `gamehub.lite`              |
| AnTuTu     | `GameHub-Lite-v{バージョン}-antutu.apk`     | `com.antutu.ABenchMark`     |
| Alt-AnTuTu | `GameHub-Lite-v{バージョン}-alt-antutu.apk` | `com.antutu.benchmark.full` |
| Ludashi    | `GameHub-Lite-v{バージョン}-ludashi.apk`    | `com.ludashi.aibench`       |
| PUBG       | `GameHub-Lite-v{バージョン}-pubg.apk`       | `com.tencent.ig`            |

バージョン番号は、ソースAPKから自動抽出されます。

## 仕組み

パッチャーは複数のステップのプロセスを使用します:

1. **デコンパイル** - apktoolを使用して元のAPKをsmaliバイトコードにデコンパイルします
2. **削除** - テレメトリSDK、未使用のアセット、およびトラッキングライブラリを削除します
3. **パッチ** - 差分パッチを適用してsmaliコードを修正
4. **追加** - 新しいファイル(リソース、追加のsmali)をコピー
5. **リビルド** - apktoolを使用してAPKをリビルド
6. **署名** - インストール用のデバッグキーストアで署名

## パッチのコンテンツ

| カテゴリー      | 数 | 説明                                 |
| ------------- | ----- | -------------------------------------------- |
| 削除     | 3,385 | テレメトリSDK、トラッキングコード、未使用アセット |
| 追加     | 2,856 | 新しいリソース、変更したアセット、新機能 |
| 変更 | 223   | 動作変更のためのsmaliコードパッチ     |

### 削除するコンポーネント

- **ネイティブライブラリ**: libumeng-spy.so、libcrashsdk.so、libalicomphonenumberauthsdk_core.soなど
- **SDK**: Umeng Analytics、JPush、Firebase Analytics、Tencent login
- **アセット**: スプラッシュの動画、認証の動画、絵文字フォント (約30MBを節約)
- **権限**: 位置情報、連絡先、電話の状態、広告の追跡

### 追加される機能

- コミュニティCDNからのカスタムスプラッシュ/イントロ動画
- ローカルゲームIDのコピー機能
- オフラインモードの改善
- ニュースページとコミュニティAPIの連携

## 開発者向け

### パッチによる生成

Lite APKを改造してパッチを更新する場合は:

```bash
./generate-patches.sh [path/to/original.apk] [path/to/lite.apk]
```

以下を行う:

1. 両方のAPKファイルをデコンパイル
2. 変更されたファイルに対応する差分パッチを生成
3. 新しいファイルをパッチディレクトリにコピー
4. 削除リストと追加リストを作成

### パッチディレクトリの構造

```
patches/
├── files_to_delete.txt    # 削除するファイルのリスト
├── files_to_add.txt       # 追加するファイルのリスト
├── files_to_patch.txt     # 変更するファイルのリスト
├── diffs/                 # 統合する差分パッチ
│   ├── AndroidManifest.xml.patch
│   ├── smali/...
│   └── res/...
├── new_files/             # 追加する新しいファイル
│   ├── res/...
│   └── smali_classes10/...
└── stats.txt              # パッチの統計
```

### パッチの変更

1. Lite APKを手動でデコンパイル:

   ```bash
   apktool d apk/GameHub-Lite.apk -o work/lite
   ```

2. `work/lite/`内のファイルに変更を追加します

3. リビルドとテスト:

   ```bash
   apktool b work/lite -o work/test.apk
   # テストのために署名してインストール
   ```

4. 満足したらパッチを再生成:
   ```bash
   ./generate-patches.sh
   ```

## トラブルシューティング

### パッチの適用に失敗しました

APKのバージョンが不一致でパッチが失敗した場合:

- GameHub 5.1.0を正確に使用していることを確認してください
- MD5ハッシュが期待値と一致していることを確認してください
- 使用しているAPKバージョンでパッチを再生成してみてください

### APKをインストールできません

- 始めに既存のGameHub Liteをアンインストールしてください
- デバイスがroot化されているか確認してください - 一部のパッチで競合する可能性があります(可能性は低いですが)

### ビルドエラー

- apktoolのバージョンが2.8.0以降であるか確認してください (`apktool --version`)
- Javaのバージョンが17以降であるか確認してください (`java -version`)
- 作業ディレクトリのクリーンアップを試してください: `rm -rf work/`

## バージョンの互換性

| GameHubのバージョン | パッチャーのバージョン | 状態         |
| --------------- | --------------- | -------------- |
| 5.1.0           | 1.0             | サポート    |
| 5.3.3           | -               | 開発中 |

## 代替: ReVancedパッチ(作業中)

`revanced/`ディレクトリには、ReVancedベースの代替パッチ適用システムが用意されています。このシステムは、バイトコードレベルの変更に[ReVanced Patcher](https://github.com/ReVanced/revanced-patcher)フレームワークを使用します。
現時点では、Lite APKのすべての機能をサポートしていませんが、不足している機能のパッチの提供や、既存の問題の解決は可能です。

```bash
cd revanced
./apply-patches.sh ../apk/GameHub-5.1.0.apk
```

詳細は[revanced/README.md](revanced/README.md)を参照してください。

### どちらを使うべきですか

| アプローチ                      | 最適な用途                                                |
| ----------------------------- | ------------------------------------------------------- |
| **差分ベース** (`./patch.sh`) | 完全なコントロール、SDKの完全な削除、よりシンプルなメンテナンス |
| **ReVanced** (`revanced/`)    | ReVanced Managerとの統合、ポータブルJARパッチ

## ライセンス

このプロジェクトは、教育目的および個人プライバシー保護のみを目的としています。パッチおよびツールは現状のまま提供されます。GameHubはそれぞれの所有者の製品です。

## 貢献

1. リポジトリをフォーク
2. 変更を加える
3. `./generate-patches.sh`を使用してパッチを再生成
4. `./patch.sh`を使用してパッチサイクル全体をテスト
5. プルリクエストを送信

## 異なるバージョンについて

各バージョンは同一です。私たちが行っているのは、一部の端末でパフォーマンスを向上させるための古いAndroidのテクニックです。

**Antutu**

一部のメーカーは、Antutuのパッケージ名を検出するとガバナーをパフォーマンスモードに設定して「不正行為」を行っています。

_オタク向けの解説:_

CPUガバナーは、基本的にCPU周波数スケーリングを制御し、システム負荷に応じて異なるクロック速度と電圧で動作できるようにします。つまり、持続的な使用のためにCPUを高速化し、本来はピーク時の使用を想定して設計されている機能を実現します。これには過熱のリスクを伴いますが、私はそうは思いません。
Androidは熱管理が十分に優れており、ソフトウェアがハードウェアの能力を超えて損傷を与えることは極めて困難だからです。

とはいえ、やはり余分な熱が発生するのは事実です、熱が増えるほどに悪影響も大きくなります。ただ、デバイスにファンが付いている場合は、その影響はごくわずかだと思います。

**PUBG**

大まかに言えばAntutuと同じですが、ゲームにのみメリットをもたらす細かな違いがいくつか存在します。ネットワークの優先順位付けやタッチ入力の遅延改善などが挙げられます。メーカーがAntutuを検出する際のゴールは、**最大限の性能**です。ベンチマークは比較的短時間で完了するため、比較サイトでの見栄えが良くなります。

一方、PUBGの場合のゴールは**さらなる性能向上**です。ゲームをより長時間プレイ可能にすることが目的であることが多いため、変更はそれほど大胆ではありません。

**TLDRと概要:**

**Antutu**の偽装:

    •    最大CPU/GPU周波数の制限を解除
    •    積極的なパフォーマンス制御
    •    短時間パフォーマンス向上(ベンチマークワークロード)
    •    サーマルスロットリングの緩和
    •    すべてのコアを使用可能

**PUBG**とその他のゲームの偽装:

    •    持続的なゲームパフォーマンスプロファイル
    •    GPUドライバの最適化(Adreno/Maliゲーム固有パス)
    •    フレームペーシングとスケジューリングの改善
    •    タッチ遅延の低減
    •    ネットワークQoSの優先順位付け
    •    異なる熱管理(持続的 vs バースト)
    •    Qualcomm謹製の「ゲームパフォーマンスモード」
    •    フレーム生成などの機能を有効化する場合あり

**Ludashi**の偽装:

    •    Antutuに似ていますが、積極さは低い
    •    より長時間持続するパフォーマンス向上(数分間に渡るテスト)
    •    メモリ周波数の最適化

## サポート

ゲームの実行に問題がある場合は、Discordサーバーでヘルプを求める前に[EmuReady](https://www.emuready.com)で誰かが解決策を共有していないか確認してください。

サポート、ディスカッション、開発状況の最新情報については、[EmuReady Discordサーバー](https://discord.gg/CYhCzApXav)にご参加ください。

---

## 関連プロジェクト

| リポジトリ                                                                               | 詳細                                                                                                                                                      |
| ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [gamehub-lite](https://github.com/Producdevity/gamehub-lite)                             | プリビルド済みのAPKリリースとパッチファイルを含むメインプロジェクト                                                                                                         |
| [gamehub-lite-api](https://github.com/Producdevity/gamehub-lite-api)                     | オリジナルの中国製サーバーを置き換える静的JSON APIホスティングコンポーネントのマニフェスト、設定ファイル、およびモックレスポンス                                  |
| [gamehub-lite-worker](https://github.com/Producdevity/gamehub-lite-worker)               | トークン管理、署名の再生成、プライバシーの保護(IPアドレスの隠蔽、フィンガープリントのサニタイズ)、コンテンツツールを処理するCloudflare Worker APIプロキシ |
| [gamehub-lite-news](https://github.com/Producdevity/gamehub-lite-news)                   | RSSフィードやGitHubリリースからゲーム関連ニュースを収集、GameHubのAPI形式に変換するニュースアグリゲーター                                          |
| [gamehub-lite-token-refresh](https://github.com/Producdevity/gamehub-lite-token-refresh) | Mail.tm OTP認証を使用して有効なGameHubトークンを維持する自動トークン更新機能は、Cloudflare Cron経由で4時間ごとに実行されます
