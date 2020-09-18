/*
 * #%L
 * de.metas.business
 * %%
 * Copyright (C) 2020 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package de.metas.product.impl;

import static org.adempiere.model.InterfaceWrapperHelper.load;
import static org.adempiere.model.InterfaceWrapperHelper.loadOutOfTrx;
import static org.adempiere.model.InterfaceWrapperHelper.newInstance;
import static org.adempiere.model.InterfaceWrapperHelper.saveRecord;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.metas.util.GuavaCollectors;
import lombok.Value;
import org.adempiere.ad.dao.IQueryBL;
import org.adempiere.exceptions.AdempiereException;
import org.adempiere.util.lang.ImmutablePair;
import org.adempiere.warehouse.WarehouseId;
import org.compiere.model.IQuery;
import org.compiere.model.I_M_Product;
import org.eevolution.model.I_PP_Product_Planning;

import de.metas.material.planning.ddorder.DistributionNetworkId;
import de.metas.material.planning.pporder.PPRoutingId;
import de.metas.organization.OrgId;
import de.metas.product.OnMaterialReceiptWithDestWarehouse;
import de.metas.product.ProductId;
import de.metas.product.ProductPlanningSchema;
import de.metas.product.ProductPlanningSchemaId;
import de.metas.product.ProductPlanningSchemaSelector;
import de.metas.product.ResourceId;
import de.metas.product.model.I_M_Product_PlanningSchema;
import de.metas.user.UserId;
import de.metas.util.Services;
import de.metas.util.StringUtils;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;

@UtilityClass
final class ProductPlanningSchemaDAO
{
	@NonNull
	public static ProductPlanningSchema getById(@NonNull final ProductPlanningSchemaId schemaId)
	{
		final I_M_Product_PlanningSchema record = loadOutOfTrx(schemaId, I_M_Product_PlanningSchema.class);
		if (record == null)
		{
			throw new AdempiereException("@NotFound@ @M_Product_PlanningSchema_ID@: " + schemaId);
		}
		return toProductPlanningSchema(record);
	}

	/**
	 * Returns the Product Planning Schema entries with the given Product Planning Schema Selector and Org. If the org does not match, fallback to org *.
	 * If there are 2 identical Product Planning Schemas, one for orgParam and one for *, we shall return only the one for orgParam.
	 */
	@NonNull
	public static ImmutableSet<ProductPlanningSchema> retrieveSchemasForSelectorAndOrg(
			@NonNull final ProductPlanningSchemaSelector productPlanningSchemaSelector,
			@NonNull final OrgId orgId)
	{
		final ImmutableMap<ProductPlanningSchemaId, ProductPlanningSchema> schemasWithDuplicateOrgs = Services.get(IQueryBL.class)
				.createQueryBuilder(I_M_Product_PlanningSchema.class)
				.addOnlyActiveRecordsFilter()
				.addOnlyContextClient()
				.addEqualsFilter(I_M_Product_PlanningSchema.COLUMNNAME_M_ProductPlanningSchema_Selector, productPlanningSchemaSelector)
				.addInArrayFilter(I_M_Product_PlanningSchema.COLUMNNAME_AD_Org_ID, orgId, OrgId.ANY)
				.create()
				.list()
				.stream()
				.map(ProductPlanningSchemaDAO::toProductPlanningSchema)
				.collect(ImmutableMap.toImmutableMap(ProductPlanningSchema::getId, Function.identity()));

		// remove duplicates where everything is the same, except the org, and repoId
		// Technical: I am using the set.add property that if an object already exists in a set, the duplicate will not be inserted but skipped.
		final Set<ProductPlanningSchemaIgnoringOrgAndId> schemasNoDuplicateOrgs = schemasWithDuplicateOrgs.values().stream()
				.sorted(Comparator.comparing(ProductPlanningSchema::getOrgId).reversed()) // * org remains last
				.map(ProductPlanningSchemaIgnoringOrgAndId::new)
				.collect(GuavaCollectors.toImmutableSet());

		final ImmutableSet.Builder<ProductPlanningSchema> result = ImmutableSet.builder();
		for (final ProductPlanningSchemaIgnoringOrgAndId s : schemasNoDuplicateOrgs)
		{
			result.add(schemasWithDuplicateOrgs.get(s.getSchema().getId()));
		}

		return result.build();
	}

	/**
	 * Used as a delegate to ProductPlanningSchema, to calculate if 2 schemas are equals, ignoring only their ID and Org
	 */

	@Value
	private static class ProductPlanningSchemaIgnoringOrgAndId
	{
		ProductPlanningSchema schema;

		@Override
		public boolean equals(final Object o)
		{
			if (schema == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			final ProductPlanningSchema that = ((ProductPlanningSchemaIgnoringOrgAndId)o).getSchema();
			return schema.isAttributeDependant() == that.isAttributeDependant() &&
					schema.isCreatePlan() == that.isCreatePlan() &&
					schema.isCompleteGeneratedDocuments() == that.isCompleteGeneratedDocuments() &&
					schema.isPickDirectlyIfFeasible() == that.isPickDirectlyIfFeasible() &&
					schema.getSelector() == that.getSelector() &&
					Objects.equals(schema.getPlantId(), that.getPlantId()) &&
					Objects.equals(schema.getWarehouseId(), that.getWarehouseId()) &&
					Objects.equals(schema.getPlannerId(), that.getPlannerId()) &&
					Objects.equals(schema.getManufactured(), that.getManufactured()) &&
					Objects.equals(schema.getRoutingId(), that.getRoutingId()) &&
					Objects.equals(schema.getDistributionNetworkId(), that.getDistributionNetworkId()) &&
					schema.getOnMaterialReceiptWithDestWarehouse() == that.getOnMaterialReceiptWithDestWarehouse();
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(schema.getSelector(), schema.getPlantId(), schema.getWarehouseId(), schema.isAttributeDependant(), schema.getPlannerId(), schema.getManufactured(), schema.isCreatePlan(), schema.isCompleteGeneratedDocuments(), schema.isPickDirectlyIfFeasible(), schema.getRoutingId(), schema.getDistributionNetworkId(), schema.getOnMaterialReceiptWithDestWarehouse());
		}
	}

	@NonNull
	private static ProductPlanningSchema toProductPlanningSchema(@NonNull final I_M_Product_PlanningSchema record)
	{
		return ProductPlanningSchema.builder()
				.id(ProductPlanningSchemaId.ofRepoId(record.getM_Product_PlanningSchema_ID()))
				.selector(ProductPlanningSchemaSelector.ofCode(record.getM_ProductPlanningSchema_Selector()))
				.orgId(OrgId.ofRepoId(record.getAD_Org_ID()))
				.plantId(ResourceId.ofRepoIdOrNull(record.getS_Resource_ID()))
				.warehouseId(WarehouseId.ofRepoIdOrNull(record.getM_Warehouse_ID()))
				.attributeDependant(record.isAttributeDependant())
				.plannerId(UserId.ofRepoIdOrNull(record.getPlanner_ID()))
				.manufactured(StringUtils.toBooleanOrNull(record.getIsManufactured()))
				.createPlan(record.isCreatePlan())
				.completeGeneratedDocuments(record.isDocComplete())
				.pickDirectlyIfFeasible(record.isPickDirectlyIfFeasible())
				.routingId(PPRoutingId.ofRepoIdOrNull(record.getAD_Workflow_ID()))
				.distributionNetworkId(DistributionNetworkId.ofRepoIdOrNull(record.getDD_NetworkDistribution_ID()))
				.onMaterialReceiptWithDestWarehouse(OnMaterialReceiptWithDestWarehouse.ofCode(record.getOnMaterialReceiptWithDestWarehouse()))
				.build();
	}

	public static void save(@NonNull final ProductPlanningSchema schema)
	{
		final I_M_Product_PlanningSchema record;
		if (schema.getId() != null)
		{
			record = load(schema.getId(), I_M_Product_PlanningSchema.class);
		}
		else
		{
			record = newInstance(I_M_Product_PlanningSchema.class);
		}

		record.setM_ProductPlanningSchema_Selector(schema.getSelector().getCode());
		record.setAD_Org_ID(schema.getOrgId().getRepoId());
		record.setS_Resource_ID(ResourceId.toRepoId(schema.getPlantId()));
		record.setM_Warehouse_ID(WarehouseId.toRepoId(schema.getWarehouseId()));
		record.setIsAttributeDependant(schema.isAttributeDependant());
		record.setPlanner_ID(UserId.toRepoId(schema.getPlannerId()));
		record.setIsManufactured(StringUtils.ofBoolean(schema.getManufactured()));
		record.setIsCreatePlan(schema.isCreatePlan());
		record.setIsDocComplete(schema.isCompleteGeneratedDocuments());
		record.setIsPickDirectlyIfFeasible(schema.isPickDirectlyIfFeasible());
		record.setAD_Workflow_ID(PPRoutingId.toRepoId(schema.getRoutingId()));
		record.setDD_NetworkDistribution_ID(DistributionNetworkId.toRepoId(schema.getDistributionNetworkId()));
		record.setOnMaterialReceiptWithDestWarehouse(schema.getOnMaterialReceiptWithDestWarehouse().getCode());

		saveRecord(record);

		schema.setId(ProductPlanningSchemaId.ofRepoId(record.getM_Product_PlanningSchema_ID()));
	}

	/**
	 * @return All the active Product Planning entries that were created for the given planning schema
	 */
	public static List<I_PP_Product_Planning> retrieveProductPlanningsForSchemaId(
			@NonNull final ProductPlanningSchemaId schemaId)
	{
		return Services.get(IQueryBL.class).createQueryBuilder(I_PP_Product_Planning.class)
				.addOnlyActiveRecordsFilter()
				.addOnlyContextClient()
				.addEqualsFilter(I_PP_Product_Planning.COLUMNNAME_M_Product_PlanningSchema_ID, schemaId)
				.create()
				.list();
	}

	/**
	 * @return All the active products with the given product planning schema selector
	 */
	public static Set<ImmutablePair<ProductId, OrgId>> retrieveProductIdsForSchemaSelector(
			@NonNull final ProductPlanningSchemaSelector productPlanningSchemaSelector)
	{
		return Services.get(IQueryBL.class)
				.createQueryBuilder(I_M_Product.class)
				.addOnlyActiveRecordsFilter()
				.addOnlyContextClient()
				.addEqualsFilter(I_M_Product.COLUMNNAME_M_ProductPlanningSchema_Selector, productPlanningSchemaSelector)
				.create()
				.listColumns(I_M_Product.COLUMNNAME_M_Product_ID, I_M_Product.COLUMNNAME_AD_Org_ID)
				.stream()
				.map(pair -> {
					final ProductId productId = ProductId.ofRepoId((int)pair.get(I_M_Product.COLUMNNAME_M_Product_ID));
					final OrgId orgId = OrgId.ofRepoId((int)pair.get(I_M_Product.COLUMNNAME_AD_Org_ID));
					return ImmutablePair.of(productId, orgId);
				})
				.collect(Collectors.toSet());
	}

	/**
	 * @return the product planning for the given product and schema if found, null otherwise.
	 */
	@Nullable
	public static I_PP_Product_Planning retrievePlanningForProductAndSchema(
			@NonNull final ProductId productId,
			@NonNull final ProductPlanningSchemaId schemaId)
	{
		return Services.get(IQueryBL.class).createQueryBuilder(I_PP_Product_Planning.class)
				.addOnlyActiveRecordsFilter()
				.addOnlyContextClient()
				.addEqualsFilter(I_PP_Product_Planning.COLUMNNAME_M_Product_ID, productId)
				.addEqualsFilter(I_PP_Product_Planning.COLUMNNAME_M_Product_PlanningSchema_ID, schemaId)
				.orderBy()
				.addColumn(I_PP_Product_Planning.COLUMN_SeqNo)
				.endOrderBy()
				.create()
				.first();
	}

	/**
	 * @return Products that don't have PP_ProductPlanning entries
	 */
	public static Stream<I_M_Product> streamProductsWithNoProductPlanningButWithSchemaSelector()
	{
		final IQueryBL queryBL = Services.get(IQueryBL.class);

		final IQuery<I_PP_Product_Planning> existentProductPlanning = queryBL.createQueryBuilder(I_PP_Product_Planning.class)
				.addOnlyActiveRecordsFilter()
				.addOnlyContextClient()
				.create();

		return queryBL.createQueryBuilder(I_M_Product.class)
				.addOnlyActiveRecordsFilter()
				.addOnlyContextClient()
				.addNotInSubQueryFilter(I_M_Product.COLUMNNAME_M_Product_ID, I_PP_Product_Planning.COLUMNNAME_M_Product_ID, existentProductPlanning)
				.addNotNull(I_M_Product.COLUMN_M_ProductPlanningSchema_Selector)
				.create()
				.iterateAndStream();
	}
}
