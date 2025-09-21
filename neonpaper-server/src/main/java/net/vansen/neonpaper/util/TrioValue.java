package net.vansen.neonpaper.util;

import org.jetbrains.annotations.NotNull;

public record TrioValue<A, B, C>(@NotNull A first, @NotNull B second, @NotNull C third) {

    public static <A, B, C> TrioValue<A, B, C> of(@NotNull A first, @NotNull B second, @NotNull C third) {
        return new TrioValue<>(first, second, third);
    }
}
