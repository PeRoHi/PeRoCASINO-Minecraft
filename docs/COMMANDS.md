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
- **/commandbook**（別名: **/cb**）
  - コマンド集ブックを受け取ります（未所持のときだけ追加）。

### 管理者（設置）

前提: 権限 **perocasino.admin**

- **/perocasino**（別名: **/pc**）
  - 管理者向けサブコマンド一覧を表示します。
- **/perocasino roulette set**
  - 見ている砥石をルーレット拠点として登録します。
- **/perocasino blackjack dealer set**
  - 近くの村人をブラックジャック ディーラーとして登録します。
- **/perocasino blackjack dealer summon**
  - ブラックジャック ディーラー村人を召喚し、登録します。
- **/perocasino hilo dealer set**
  - 近くの村人を H&L ディーラーとして登録します。
- **/perocasino hilo dealer summon**
  - H&L ディーラー村人を召喚し、登録します。
- **/perocasino quarry set**
  - 採石場の立方体範囲を、実行した地点の角として登録します（2回実行で確定）。
- **/perocasino reload**
  - `config.yml` を再読込します。
