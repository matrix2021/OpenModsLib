package openmods.model.textureditem;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.IModelCustomData;
import net.minecraftforge.client.model.IPerspectiveAwareModel;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.ModelStateComposition;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import openmods.model.BakedModelAdapter;
import openmods.model.ModelUpdater;

public class TexturedItemModel implements IModelCustomData {

	private static class BakedModel extends BakedModelAdapter {

		private final ItemOverrideList overrideList;

		public BakedModel(IBakedModel base, ImmutableMap<TransformType, TRSRTransformation> cameraTransforms, ItemOverrideList itemOverrideList) {
			super(base, cameraTransforms);
			this.overrideList = itemOverrideList;
		}

		@Override
		public ItemOverrideList getOverrides() {
			return overrideList;
		}
	}

	public static final TexturedItemModel INSTANCE = new TexturedItemModel();

	private final Optional<ResourceLocation> untexturedModel;
	private final Optional<ResourceLocation> texturedModel;
	private final Set<String> textures;

	private TexturedItemModel() {
		untexturedModel = Optional.absent();
		texturedModel = Optional.absent();
		textures = ImmutableSet.of();
	}

	private TexturedItemModel(Optional<ResourceLocation> empty, Optional<ResourceLocation> fluid, Set<String> textures) {
		this.untexturedModel = empty;
		this.texturedModel = fluid;
		this.textures = ImmutableSet.copyOf(textures);
	}

	@Override
	public Collection<ResourceLocation> getDependencies() {
		return Sets.union(untexturedModel.asSet(), texturedModel.asSet());
	}

	@Override
	public Collection<ResourceLocation> getTextures() {
		return ImmutableSet.of();
	}

	@Override
	public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
		final IModel untexturedModel = getModel(this.untexturedModel);
		final IBakedModel untexturedBakedModel = untexturedModel.bake(new ModelStateComposition(state, untexturedModel.getDefaultState()), format, bakedTextureGetter);

		final IModel texturedModel = getModel(this.texturedModel);
		final IBakedModel texturedBakedModel = texturedModel.bake(new ModelStateComposition(state, texturedModel.getDefaultState()), format, bakedTextureGetter);

		final ItemOverrideList overrides = new TexturedItemOverrides(untexturedBakedModel, texturedModel, texturedBakedModel.getOverrides().getOverrides(), textures, state, format, bakedTextureGetter);

		return new BakedModel(untexturedBakedModel, IPerspectiveAwareModel.MapWrapper.getTransforms(state), overrides);
	}

	private static IModel getModel(Optional<ResourceLocation> model) {
		if (model.isPresent()) {
			ResourceLocation location = model.get();
			return ModelLoaderRegistry.getModelOrLogError(location, "Couldn't load base-model dependency: " + location);
		} else {
			return ModelLoaderRegistry.getMissingModel();
		}
	}

	@Override
	public IModelState getDefaultState() {
		return TRSRTransformation.identity();
	}

	@Override
	public IModel process(ImmutableMap<String, String> customData) {
		final ModelUpdater updater = new ModelUpdater(customData);

		final Optional<ResourceLocation> untexturedModel = updater.get("untexturedModel", ModelUpdater.MODEL_LOCATION, this.untexturedModel);
		final Optional<ResourceLocation> filled = updater.get("texturedModel", ModelUpdater.MODEL_LOCATION, this.texturedModel);
		final Set<String> textures = updater.get("textures", ModelUpdater.TO_STRING, this.textures);

		return updater.hasChanged()? new TexturedItemModel(untexturedModel, filled, textures) : this;
	}

}
