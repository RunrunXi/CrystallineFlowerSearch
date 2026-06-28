package me.cyanhana.cfs.mixin;

import com.telepathicgrunt.the_bumblezone.client.screens.CrystallineFlowerScreen;
import me.cyanhana.cfs.SearchBoxAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {

    @Inject(method = "init", at = @At("TAIL"))
    private void cfs$initSearchBox(CallbackInfo ci) {
        // 只对 CrystallineFlowerScreen 实例生效
        if (((Object) this) instanceof SearchBoxAccessor accessor) {
            accessor.cfs$createSearchBox();
        }
    }
}