package me.cyanhana.cfs.mixin;

import com.telepathicgrunt.the_bumblezone.client.screens.CrystallineFlowerScreen;
import com.telepathicgrunt.the_bumblezone.menus.CrystallineFlowerMenu;
import me.cyanhana.cfs.SearchBoxAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Locale;

@Mixin(CrystallineFlowerScreen.class)
public abstract class CrystallineFlowerScreenMixin extends AbstractContainerScreen<CrystallineFlowerMenu> implements SearchBoxAccessor {

    @Unique
    private static final boolean JECHARACTERS_LOADED = cfs$checkJecharacters();

    @Shadow(remap = false)
    private int startIndex;
    @Shadow(remap = false)
    private float scrollOff;
    @Shadow(remap = false)
    private boolean scrolling;

    @Unique
    private static boolean cfs$checkJecharacters() {
        try {
            Class.forName("me.towdium.jecharacters.utils.Match");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Unique
    private EditBox cfs$searchBox;

    public CrystallineFlowerScreenMixin(CrystallineFlowerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * 公开的初始化搜索框方法，由 AbstractContainerScreenMixin 调用
     */
    @Unique
    public void cfs$createSearchBox() {
        String previousSearch = this.cfs$searchBox == null ? "" : this.cfs$searchBox.getValue();
        int startX = (this.width - this.imageWidth) / 2;
        int startY = (this.height - this.imageHeight) / 2;

        this.cfs$searchBox = new EditBox(
                this.font,
                startX + 76,
                startY - 15,
                86,
                12,
                Component.empty()
        );
        this.cfs$searchBox.setMaxLength(50);
        this.cfs$searchBox.setHint(Component.translatable("gui.cfs.search_hint"));
        this.cfs$searchBox.setValue(previousSearch);
        this.cfs$searchBox.setResponder(this::cfs$onSearchChanged);
        this.addRenderableWidget(this.cfs$searchBox);
    }

    /**
     * 搜索内容变化时的回调
     */
    @Unique
    public void cfs$onSearchChanged(String searchText) {
        // 从当前有效数据重建，绝不能恢复物品/经验更新前的旧附魔 ID。
        // 排序尾部的注入会通过接口重新应用搜索条件。
        CrystallineFlowerScreen.SortAndAssignAvailableEnchants();
        cfs$resetScrollState();
    }

    @Override
    @Unique
    public void cfs$refreshSearchResults() {
        String filter = this.cfs$searchBox == null ? ""
                : this.cfs$searchBox.getValue().toLowerCase(Locale.ROOT).trim();
        List<ResourceLocation> filtered = CrystallineFlowerScreen.enchantmentsAvailableSortedList.stream()
                .filter(rl -> {
                    var skeleton = CrystallineFlowerScreen.enchantmentsAvailable.get(rl);
                    if (skeleton == null) return false;
                    if (filter.isEmpty()) return true;

                    // 翻译键
                    String translationKey = "enchantment." + skeleton.namespace + "." + skeleton.path;

                    // 获取翻译后的名称
                    String translatedName = net.minecraft.locale.Language.getInstance()
                            .getOrDefault(translationKey)
                            .toLowerCase(Locale.ROOT);

                    // 先进行常规搜索（翻译键、翻译名、路径、命名空间）
                    if (translationKey.toLowerCase(Locale.ROOT).contains(filter)
                            || translatedName.contains(filter)
                            || skeleton.path.toLowerCase(Locale.ROOT).contains(filter)
                            || skeleton.namespace.toLowerCase(Locale.ROOT).contains(filter)) {
                        return true;
                    }

                    // 如果安装了拼音模组，再进行拼音匹配
                    if (JECHARACTERS_LOADED) {
                        return cfs$pinyinMatch(translatedName, filter);
                    }

                    return false;
                })
                .toList();

        CrystallineFlowerScreen.enchantmentsAvailableSortedList.clear();
        CrystallineFlowerScreen.enchantmentsAvailableSortedList.addAll(filtered);

        // 数据更新可能缩短列表；同时修正滚动索引，避免原界面的越界访问。
        int maxIndex = Math.max(0, CrystallineFlowerScreen.enchantmentsAvailableSortedList.size() - 3);
        this.startIndex = Math.max(0, Math.min(this.startIndex, maxIndex));
        this.scrollOff = maxIndex > 0 ? (float) this.startIndex / maxIndex : 0.0F;
        this.scrolling = false;
    }

    @Unique
    private void cfs$resetScrollState() {
        startIndex = 0;
        scrollOff = 0.0F;
        scrolling = false;
    }

    @Unique
    private boolean cfs$pinyinMatch(String translatedName, String filter) {
        try {
            // 通过反射调用 Match.contains()，避免直接 import
            return (boolean) Class
                    .forName("me.towdium.jecharacters.utils.Match")
                    .getMethod("contains", String.class, CharSequence.class)
                    .invoke(null, translatedName, filter);
        } catch (Exception e) {
            // 如果反射失败，安全地返回 false，不影响正常搜索
            return false;
        }
    }

    /**
     * 在更新附魔列表后重新应用搜索过滤
     */
    @Inject(method = "SortAndAssignAvailableEnchants", at = @At("TAIL"), remap = false)
    private static void cfs$afterSortAndAssign(CallbackInfo ci) {
        // 通过接口访问注入方法，避免直接引用 Mixin 类型。
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof SearchBoxAccessor accessor) {
            accessor.cfs$refreshSearchResults();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void cfs$handleSearchBoxFocus(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (this.cfs$searchBox != null) {
            if (cfs$searchBox.isMouseOver(mouseX, mouseY)) {
                // 右键点击
                if (button == 1) {
                    // 清空搜索框
                    this.cfs$searchBox.setValue("");
                }
            } else {
                // 点击其他地方 -> 移除焦点
                this.cfs$searchBox.setFocused(false);
                this.setFocused(null);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 如果搜索框有焦点，优先处理
        if (this.cfs$searchBox != null && this.cfs$searchBox.isFocused()) {
            // ESC：移除焦点但不关闭界面
            if (keyCode == 256) {
                this.cfs$searchBox.setFocused(false);
                this.setFocused(null);
                return true;
            }
            // 搜索框处理输入
            if (this.cfs$searchBox.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // 有焦点时仅能输入
            return true;
        }

        // 下面是原版的逻辑（保持和父类一致）
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

}
