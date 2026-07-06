package me.cyanhana.cfs.mixin;

import com.telepathicgrunt.the_bumblezone.client.screens.CrystallineFlowerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CrystallineFlowerScreen.class)
public interface ICrystallineFlowerScreenAccessor {

    @Accessor
    void setStartIndex(int index);

    @Accessor
    void setScrollOff(float scrollOff);

    @Accessor
    void setScrolling(boolean scrolling);
}