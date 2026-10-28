package model;

import java.time.LocalDate;

/**
 * One inventory row in the current session (no persistence across runs).
 * <p>
 * 当前会话中的一条库存记录；不落盘，结账后会话清空时一并清除。
 */
public class FoodItem {
    /** Unique id for list keys and updates. / 列表键与更新操作用的唯一 id。 */
    private final String id;
    /** Canonical food name (from catalog suggestions). / 规范食材名（来自目录建议）。 */
    private final String name;
    /** Ingredient category for inventory filters. / 用于库存筛选的食材分类。 */
    private final FoodCategory category;
    /** Amount on hand. / 当前数量。 */
    private final int quantity;
    /** Unit label (e.g. pcs). / 单位文案（如 pcs）。 */
    private final String unit;
    /** ISO date string when the item was added (date granularity). / ISO 日期字符串，表示入库日（按日粒度）。 */
    private final String createdAt;
    /** ISO date string for expiry. / ISO 日期字符串，表示过期日。 */
    private final String expiryDate;

    /**
     * Builds an immutable inventory snapshot.
     * <p>
     * 构造不可变的库存快照。
     */
    public FoodItem(
            String id,
            String name,
            FoodCategory category,
            int quantity,
            String unit,
            String createdAt,
            String expiryDate) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unit = unit;
        this.createdAt = createdAt;
        this.expiryDate = expiryDate;
    }

    /** Returns item id. / 返回条目 id。 */
    public String getId() {
        return id;
    }

    /** Returns canonical food name. / 返回规范食材名。 */
    public String getName() {
        return name;
    }

    /** Returns ingredient category. / 返回食材分类。 */
    public FoodCategory getCategory() {
        return category;
    }

    /** Returns quantity. / 返回数量。 */
    public int getQuantity() {
        return quantity;
    }

    /** Returns unit string. / 返回单位字符串。 */
    public String getUnit() {
        return unit;
    }

    /** Returns created-at date string. / 返回创建/入库日期字符串。 */
    public String getCreatedAt() {
        return createdAt;
    }

    /** Returns expiry date string. / 返回过期日期字符串。 */
    public String getExpiryDate() {
        return expiryDate;
    }

    /**
     * PRD "new" item: created on or after yesterday (date-level model).
     * <p>
     * PRD「新品」：创建日在昨天及之后（按日期粒度判断）。
     */
    public boolean isNew() {
        LocalDate created = LocalDate.parse(createdAt);
        return !created.isBefore(LocalDate.now().minusDays(1));
    }

    /**
     * PRD "urgent" item: expires today.
     * <p>
     * PRD「紧急」：过期日为今天。
     */
    public boolean isUrgent() {
        LocalDate expiry = LocalDate.parse(expiryDate);
        return expiry.isEqual(LocalDate.now());
    }

    /** Expired or depleted batches must not count as ingredients available for cooking. */
    public boolean isAvailableForCooking() {
        return quantity > 0 && !LocalDate.parse(expiryDate).isBefore(LocalDate.now());
    }
}
