package openmods.calc.types.multi;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.reflect.TypeToken;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import openmods.calc.BinaryOperator;
import openmods.calc.types.multi.TypeDomain.Coercion;
import openmods.reflection.TypeVariableHolder;

public class TypedBinaryOperator extends BinaryOperator<TypedValue> {

	public static class TypeVariableHolders {
		@TypeVariableHolder(ICoercedOperation.class)
		public static class CoercedOperation {
			public static TypeVariable<?> T;
		}

		@TypeVariableHolder(ISimpleCoercedOperation.class)
		public static class SimpleCoercedOperation {
			public static TypeVariable<?> T;
		}

		@TypeVariableHolder(IVariantOperation.class)
		public static class VariantOperation {
			public static TypeVariable<?> L;
			public static TypeVariable<?> R;
		}

		@TypeVariableHolder(ISimpleVariantOperation.class)
		public static class SimpleVariantOperation {
			public static TypeVariable<?> L;
			public static TypeVariable<?> R;
			public static TypeVariable<?> O;
		}
	}

	public TypedBinaryOperator(String id, int precedence, openmods.calc.BinaryOperator.Associativity associativity) {
		super(id, precedence, associativity);
	}

	public TypedBinaryOperator(String id, int precendence) {
		super(id, precendence);
	}

	@SuppressWarnings("unchecked")
	private static <T> Class<T> resolveVariable(TypeToken<?> token, TypeVariable<?> var) {
		return (Class<T>)token.resolveType(var).getRawType();
	}

	private interface IGenericOperation {
		public TypedValue apply(TypeDomain domain, TypedValue left, TypedValue right);
	}

	public interface ICoercedOperation<T> {
		public TypedValue apply(TypeDomain domain, T left, T right);
	}

	public interface ISimpleCoercedOperation<T> {
		public T apply(T left, T right);
	}

	private final Map<Class<?>, IGenericOperation> coercedOperations = Maps.newHashMap();

	public <T> TypedBinaryOperator registerOperation(final Class<T> type, final ICoercedOperation<T> op) {
		final IGenericOperation prev = coercedOperations.put(type, new IGenericOperation() {
			@Override
			public TypedValue apply(TypeDomain domain, TypedValue left, TypedValue right) {
				final T leftValue = left.unwrap(type);
				final T rightValue = right.unwrap(type);
				return op.apply(domain, leftValue, rightValue);
			}

		});

		Preconditions.checkState(prev == null, "Duplicate operation registration on operator '%s', type: %s", id, type);
		return this;
	}

	public <T> TypedBinaryOperator registerOperation(final Class<T> type, final ISimpleCoercedOperation<T> op) {
		return registerOperation(type, new ICoercedOperation<T>() {
			@Override
			public TypedValue apply(TypeDomain domain, T left, T right) {
				final T result = op.apply(left, right);
				return domain.create(type, result);
			}
		});
	}

	public <T> TypedBinaryOperator registerOperation(ICoercedOperation<T> op) {
		final TypeToken<?> token = TypeToken.of(op.getClass());
		final Class<T> type = resolveVariable(token, TypeVariableHolders.CoercedOperation.T);
		return registerOperation(type, op);
	}

	public <T> TypedBinaryOperator registerOperation(ISimpleCoercedOperation<T> op) {
		final TypeToken<?> token = TypeToken.of(op.getClass());
		final Class<T> type = resolveVariable(token, TypeVariableHolders.SimpleCoercedOperation.T);
		return registerOperation(type, op);
	}

	public interface IVariantOperation<L, R> {
		public TypedValue apply(TypeDomain domain, L left, R right);
	}

	public interface ISimpleVariantOperation<L, R, O> {
		public O apply(L left, R right);
	}

	private final Table<Class<?>, Class<?>, IGenericOperation> variantOperations = HashBasedTable.create();

	public <L, R> TypedBinaryOperator registerOperation(final Class<? extends L> left, final Class<? extends R> right, final IVariantOperation<L, R> op) {
		final IGenericOperation prev = variantOperations.put(left, right, new IGenericOperation() {
			@Override
			public TypedValue apply(TypeDomain domain, TypedValue leftArg, TypedValue rightArg) {
				final L leftValue = leftArg.unwrap(left);
				final R rightValue = rightArg.unwrap(right);
				return op.apply(domain, leftValue, rightValue);
			}

		});
		Preconditions.checkState(prev == null, "Duplicate operation registration on operator '%s', types: %s, %s", id, left, right);
		return this;
	}

	public <L, R, O> TypedBinaryOperator registerOperation(Class<? extends L> left, Class<? extends R> right, final Class<? super O> output, final ISimpleVariantOperation<L, R, O> op) {
		return registerOperation(left, right, new IVariantOperation<L, R>() {
			@Override
			public TypedValue apply(TypeDomain domain, L left, R right) {
				final O result = op.apply(left, right);
				return domain.create(output, result);
			}
		});
	}

	public <L, R> TypedBinaryOperator registerOperation(IVariantOperation<L, R> op) {
		final TypeToken<?> token = TypeToken.of(op.getClass());
		final Class<L> left = resolveVariable(token, TypeVariableHolders.VariantOperation.L);
		final Class<R> right = resolveVariable(token, TypeVariableHolders.VariantOperation.R);
		return registerOperation(left, right, op);
	}

	public <L, R, O> TypedBinaryOperator registerOperation(ISimpleVariantOperation<L, R, O> op) {
		final TypeToken<?> token = TypeToken.of(op.getClass());
		final Class<L> left = resolveVariable(token, TypeVariableHolders.SimpleVariantOperation.L);
		final Class<R> right = resolveVariable(token, TypeVariableHolders.SimpleVariantOperation.R);
		final Class<O> output = resolveVariable(token, TypeVariableHolders.SimpleVariantOperation.O);
		return registerOperation(left, right, output, op);
	}

	public interface IDefaultOperation {
		public Optional<TypedValue> apply(TypeDomain domain, TypedValue left, TypedValue right);
	}

	private IDefaultOperation defaultOperation;

	public TypedBinaryOperator setDefaultOperation(IDefaultOperation defaultOperation) {
		this.defaultOperation = defaultOperation;
		return this;
	}

	@Override
	protected TypedValue execute(TypedValue left, TypedValue right) {
		Preconditions.checkArgument(left.domain == right.domain, "Values from different domains: %s, %s", left, right);
		final TypeDomain domain = left.domain;

		final Coercion coercionRule = domain.getCoercionRule(left.type, right.type);
		if (coercionRule == Coercion.TO_LEFT) {
			final IGenericOperation op = coercedOperations.get(left.type);
			if (op != null) return op.apply(domain, left, right);
		} else if (coercionRule == Coercion.TO_RIGHT) {
			final IGenericOperation op = coercedOperations.get(right.type);
			if (op != null) return op.apply(domain, left, right);
		}

		final IGenericOperation op = variantOperations.get(left.type, right.type);
		if (op != null) return op.apply(domain, left, right);

		if (defaultOperation != null) {
			final Optional<TypedValue> result = defaultOperation.apply(domain, left, right);
			if (result.isPresent()) return result.get();
		}

		throw new IllegalArgumentException(String.format("Can't apply operation '%s' on values %s,%s", id, left, right));
	}
}
