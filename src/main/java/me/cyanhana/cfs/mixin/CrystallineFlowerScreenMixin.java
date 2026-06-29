package me.cyanhana.cfs.mixin;

import com.telepathicgrunt.the_bumblezone.client.screens.CrystallineFlowerScreen;
import com.telepathicgrunt.the_bumblezone.menus.CrystallineFlowerMenu;
import me.cyanhana.cfs.SearchBoxAccessor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(CrystallineFlowerScreen.class)
public abstract class CrystallineFlowerScreenMixin extends AbstractContainerScreen<CrystallineFlowerMenu> implements SearchBoxAccessor {

    @Unique
    private static final boolean JECHARACTERS_LOADED = cfs$checkJecharacters();

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

    @Unique
    private final List<ResourceLocation> cfs$filteredEnchantmentList = new ArrayList<>();

    public CrystallineFlowerScreenMixin(CrystallineFlowerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * 公开的初始化搜索框方法，由 AbstractContainerScreenMixin 调用
     */
    @Unique
    public void cfs$createSearchBox() {
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
        this.cfs$searchBox.setResponder(this::cfs$onSearchChanged);
        this.addRenderableWidget(this.cfs$searchBox);
    }

    /**
     * 搜索内容变化时的回调
     */
    @Unique
    private void cfs$onSearchChanged(String searchText) {
        String filter = searchText.toLowerCase(Locale.ROOT).trim();

        if (filter.isEmpty()) {
            // 无搜索内容时恢复原始列表
            if (!this.cfs$filteredEnchantmentList.isEmpty()) {
                CrystallineFlowerScreen.enchantmentsAvailableSortedList.clear();
                CrystallineFlowerScreen.enchantmentsAvailableSortedList.addAll(this.cfs$filteredEnchantmentList);
                this.cfs$filteredEnchantmentList.clear();
            }
            return;
        }

        // 首次过滤时保存原始列表
        if (this.cfs$filteredEnchantmentList.isEmpty()) {
            this.cfs$filteredEnchantmentList.addAll(CrystallineFlowerScreen.enchantmentsAvailableSortedList);
        }

        List<ResourceLocation> filtered = this.cfs$filteredEnchantmentList.stream()
                .filter(rl -> {
                    var skeleton = CrystallineFlowerScreen.enchantmentsAvailable.get(rl);
                    if (skeleton == null) return false;

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
                        System.out.println("开始比对: " + translatedName + " 与 " + filter);
                        return cfs$pinyinMatch(translatedName, filter);
                    }

                    return false;
                })
                .toList();

        CrystallineFlowerScreen.enchantmentsAvailableSortedList.clear();
        CrystallineFlowerScreen.enchantmentsAvailableSortedList.addAll(filtered);
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
     * 关闭界面时清理搜索状态
     */
    @Inject(method = "onClose", at = @At("HEAD"))
    private void cfs$onCloseCleanup(CallbackInfo ci) {
        if (!this.cfs$filteredEnchantmentList.isEmpty()) {
            CrystallineFlowerScreen.enchantmentsAvailableSortedList.clear();
            CrystallineFlowerScreen.enchantmentsAvailableSortedList.addAll(this.cfs$filteredEnchantmentList);
            this.cfs$filteredEnchantmentList.clear();
        }
    }

    /**
     * 当附魔列表被重新填充时，如果当前有搜索条件则重新应用过滤
     */
    @Inject(method = "populateAvailableEnchants", at = @At("TAIL"), remap = false)
    private void cfs$onPopulateEnchants(CallbackInfo ci) {
        if (this.cfs$searchBox != null && !this.cfs$searchBox.getValue().isEmpty()) {
            this.cfs$filteredEnchantmentList.clear();
            this.cfs$onSearchChanged(this.cfs$searchBox.getValue());
        }
    }
}
