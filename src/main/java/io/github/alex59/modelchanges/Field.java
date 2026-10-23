package io.github.alex59.modelchanges;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * A named projection and its equivalence policy. Extractors and policies should
 * be pure and safe for concurrent calls if the owning differ is shared.
 *
 * @param <T> model type
 * @param <V> projected field type
 */
public final class Field<T, V> {
    private final String name;
    private final Function<? super T, ? extends V> extractor;
    private final BiPredicate<? super V, ? super V> equivalent;

    private Field(String name, Function<? super T, ? extends V> extractor,
                  BiPredicate<? super V, ? super V> equivalent) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) throw new IllegalArgumentException("Field name must not be blank");
        this.extractor = Objects.requireNonNull(extractor, "extractor");
        this.equivalent = Objects.requireNonNull(equivalent, "equivalent");
    }

    /**
     * Creates a field using {@link Objects#deepEquals(Object, Object)}. Nulls and
     * arrays are supported; model objects otherwise use their equals method.
     *
     * @param name unique field name
     * @param extractor value projection
     * @param <T> model type
     * @param <V> field type
     * @return field definition
     */
    public static <T, V> Field<T, V> of(String name, Function<? super T, ? extends V> extractor) {
        return comparing(name, extractor, Objects::deepEquals);
    }

    /**
     * Creates a field with explicit equivalence. The policy receives null values
     * unchanged, so it must implement the desired null behavior itself.
     *
     * @param name unique field name
     * @param extractor value projection
     * @param equivalent true when the values should be treated as equal
     * @param <T> model type
     * @param <V> field type
     * @return field definition
     */
    public static <T, V> Field<T, V> comparing(String name,
            Function<? super T, ? extends V> extractor,
            BiPredicate<? super V, ? super V> equivalent) {
        return new Field<>(name, extractor, equivalent);
    }

    /** Returns the configured field name.
     * @return the field name used in change reports */
    public String name() { return name; }

    Optional<FieldChange> compare(T before, T after) {
        V oldValue = extractor.apply(before);
        V newValue = extractor.apply(after);
        return equivalent.test(oldValue, newValue) ? Optional.empty()
                : Optional.of(new FieldChange(name, oldValue, newValue));
    }
}
