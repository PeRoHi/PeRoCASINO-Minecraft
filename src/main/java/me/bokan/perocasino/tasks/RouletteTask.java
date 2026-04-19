public class RouletteTask extends BukkitRunnable {
    private final Player player;
    private final Inventory gui;
    private final int targetIndex; // 確率で決めた「当たり」の位置 (0-8)
    private int currentIndex;      // 現在の描画位置
    private int ticks = 0;
    private int delay = 1;         // 回転の速さ (1 = 最速)

    public RouletteTask(Player player, Inventory gui, int startIndex, int targetIndex) {
        this.player = player;
        this.gui = gui;
        this.currentIndex = startIndex;
        this.targetIndex = targetIndex;
    }

    @Override
    public void run() {
        ticks++;
        
        // アイテムを 1 つ進める
        currentIndex = (currentIndex + 1) % 9;
        updateGui(currentIndex);

        // 減速ロジック：停止位置が近づいたら delay を増やしてゆっくりにする
        if (ticks > 40 && currentIndex == (targetIndex - 3 + 9) % 9) {
            delay = 3;
        } else if (ticks > 50 && currentIndex == (targetIndex - 1 + 9) % 9) {
            delay = 10;
        }

        // 停止判定
        if (ticks > 60 && currentIndex == targetIndex) {
            this.cancel();
            player.sendMessage("§a§l[ルーレット] 結果確定！");
            // ここで EconomyManager に lastRouletteIndex を保存
            // 結果に応じた効果（借金倍増など）を発動
        }
    }

    private void updateGui(int index) {
        // インベントリの特定スロットにアイテムをセットする処理
        // index に基づいてアイテムをずらして描画する
    }
}