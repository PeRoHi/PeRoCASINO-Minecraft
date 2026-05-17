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

- `config.yml` の **`command-wand`** で、特定アイテム（デフォルト **人参付きの棒 = `CARROT_ON_A_STICK`**）を **メインハンドで右クリック（使用）** したときに、列挙したコマンドをプレイヤーとして順に実行できます。
- **入手方法（プラグインは自動配布しません）**
  - クラフト: 釣り竿 + ニンジン → 人参付きの棒
  - OP/管理者: `/give @s carrot_on_a_stick`
  - リソースパックで見た目を変えたい場合は `match-custom-model-data` と CMD を合わせる
- **`command-wand.enabled`** を `true` にし、**`command-wand.commands`** に実行したい行（先頭の `/` はあってもなくても可）を書きます。
- 改ざん防止のため、**`command-wand.allowed-command-labels`** にある「コマンドの先頭ラベル」だけが実行されます（例: `chinchiro`, `casino`, `perocasino`）。
- 権限: デフォルトでは **`perocasino.commandwand`**（`plugin.yml` では **op** 想定）。`command-wand.permission` で変更可能です。
- ブタン用の **Interaction** ではなく、**アイテム使用**トリガです。豚に乗っているときは **`skip-when-riding-pig`**（既定 `true`）で無効化されます。
