package kr.ac.jbnu.se.tetris.battle;

/** 모든 참가자에게 같은 최대 HP 및 최소 HP 경계를 적용 */
public final class HPManager {
    public int applyDamage(int currentHp, int maxHp, int damage) {
        if (maxHp <= 0 || currentHp < 0 || currentHp > maxHp || damage < 0) {
            throw new IllegalArgumentException("Invalid HP or damage");
        }
        return Math.max(0, currentHp - damage);
    }

    public int applyHealing(int currentHp, int maxHp, int healing) {
        if (maxHp <= 0 || currentHp < 0 || currentHp > maxHp || healing < 0) {
            throw new IllegalArgumentException("Invalid HP or healing");
        }
        return (int) Math.min((long) maxHp, (long) currentHp + healing);
    }
}
