## PeRoCasino コマンド集

このページは **コマンド集** です。ゲームのルールは `docs/RULEBOOK.md` を参照してください。

### 一般

- **/casino**
  - カジノメニューを開きます。
- **/casino <プレイヤー名|セレクター>**
  - 対象プレイヤーにカジノメニューを開きます。
- **/balance**（別名: **/bal**）
  - 財布残高と借金額を表示します。
- **/deposit**
  - メインハンドのダイヤをすべて財布へ預け入れます。
- **/hilo select <high|low>**
  - 進行中の High & Low で High/Low を選択します。
  - 実装上、次も同じ意味で使えます: **/hilo high|low|h|l|hi|lo**
- **/chinchiro roll**
  - チンチロ用のサイコロを3個振り、設定された表示領域に出目を表示します（ルール判定や賭けは含みません）。
  - 権限: **perocasino.chinchiro.roll**（`plugin.yml` ではデフォルトで付与）
- **/commandbook**（別名: **/cb**）
  - コマンド集ブックを受け取ります（未所持のときだけ追加）。
- **/commandbook refresh**（**update** も可）
  - インベントリ内のコマンド集を**最新版の内容に差し替え**ます。古い3ページ版を持っている場合はこちらを実行してください。

### 管理者（設置）

前提: 権限 **perocasino.admin**

- **/perocasino**（別名: **/pc**）
  - 管理者向けサブコマンド一覧を表示します。

#### ルーレット（砥石）の設置

- **/perocasino roulette set**（別名: **/pc roulette set**）
  - **6ブロック以内**で見ている **砥石（grindstone）** をルーレット拠点として `config.yml` に登録します。
  - プレイヤーがその砥石を右クリックしてベット GUI を開きます。
- **/perocasino roulette display set**（別名: **/pc roulette display set**）
  - **8ブロック以内**で見ているブロックの面を基準に、ルーレット盤面の **ItemDisplay** を設置・config に保存します。
- **/perocasino roulette display remove**（別名: **/pc roulette display remove**）
  - 保存されているルーレット **ItemDisplay** を削除します。
- **/perocasino roulette remove**
  - ルーレット拠点（砥石）の登録を削除します。
- **/perocasino roulette start** / **/perocasino roulette stop**
  - ルーレットの自動進行を再開 / 一時停止します（`reload` で反映）。
- **/perocasino roulette board set**
  - 砥石ベット盤の**左端の砥石**を登録します（プレイヤーの向きから facing を保存）。
- **/perocasino blackjack dealer set**
  - 近くの村人をブラックジャック ディーラーとして登録します。
- **/perocasino blackjack dealer summon**
  - ブラックジャック ディーラー村人を召喚し、登録します。
- **/perocasino hilo dealer set**
  - 近くの村人を H&L ディーラーとして登録します。
- **/perocasino hilo dealer summon**
  - H&L ディーラー村人を召喚し、登録します。
- **/perocasino chinchiro dealer set** / **summon**
  - チンチロ卓の村人ディーラーを登録 / 召喚します。
- **/perocasino chinchiro region set**（別名: **/pc chinchiro region set**）
  - チンチロ用サイコロ3個の出現範囲（AABB）を、実行した地点の角として登録します（2回実行: 1回目がMIN角、2回目がMAX角）。
  - 登録後、プレイヤーは **/chinchiro roll** でサイコロを振れます（卓ディーラーからの参加も可）。
- **/perocasino quarry set**
  - 採石場の立方体範囲を、実行した地点の角として登録します（2回実行で確定）。
- **/perocasino slot create <id>**
  - 設置スロット（TextDisplay）を現在位置に登録します（`slot-display.enabled` が `true` になります）。
- **/perocasino slot remove <id>**
  - 設置スロットを設定から削除します。
- **/perocasino slot list**
  - 設置スロットの一覧を表示します。
- **/perocasino slot dealer set**（別名: **/pc slot dealer set**）
  - 近くの村人を「設置スロット掛け金ディーラー」として登録します。
- **/perocasino slot dealer summon**（別名: **/pc slot dealer summon**）
  - 掛け金ディーラー村人を召喚し、登録します。
- **/perocasino reload**
  - `config.yml` を再読込します。

### コマンド杖（config: `command-wand`）

- 既定素材は **人参付きの棒**（`CARROT_ON_A_STICK`）。**メインハンドで右クリック**で実行します。
- **表示名でコマンドを切り替え**: `command-wand.wands` に「杖の表示名 → 実行コマンド列」を登録します（色コードは無視して一致）。
- **テレポート杖**: 表示名を **`tp-100-64-200`** のようにすると `tp 100 64 200` を実行（`wands` への登録は不要）。
- **旧形式** `command-wand.commands` は、**表示名が付いていない杖**のときだけ使われます（互換用・削除しない）。
- 改ざん防止: **`command-wand.allowed-command-labels`** にある先頭ラベルのみ実行可能。
- 権限: **`perocasino.commandwand`**（既定 op）。`command-wand.enabled: true` が必要。

#### 初期登録されている杖の表示名（`config.yml`）

| 表示名 | 実行内容（概要） |
|--------|------------------|
| BJディーラー設置 | `/perocasino blackjack dealer set` |
| H&Lディーラー設置 | `/perocasino hilo dealer set` |
| スロット掛け金ディーラー設置 | `/perocasino slot dealer set` |
| チンチロディーラー設置 | `/perocasino chinchiro dealer set` |
| ルーレット設置 | `/perocasino roulette display set` |
| ルーレット拠点設置 | `/perocasino roulette set` |
| スロット設置 | `/perocasino slot create <slot-create-id>` |
| サイコロの場所設置 | `/perocasino chinchiro region set`（角2回） |
| サイコロ振る | `/chinchiro roll` |
| コマンド集付与 / コマンドブロック付与 | `/commandbook` |
| サバイバル変更 | `/gamemode survival` |
| クリエイティブ変更 | `/gamemode creative` |
| `tp-X-Y-Z` | `/tp X Y Z` |

追加するときは `wands` に行を足すだけで OK です（既存の行は消さない運用）。

#### 入手・名前の付け方

- クラフトまたは `/give @s carrot_on_a_stick`
- Anvil やコマンドで **DisplayName** を上表と同じ文字列にする（例: `/give @s carrot_on_a_stick[custom_name='{"text":"ルーレット設置"}']` はバージョンにより書式が異なります）
