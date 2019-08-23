import { BPartner } from '../../support/utils/bpartner';
import { BPartnerLocation } from '../../support/utils/bpartner_ui';
import { DiscountSchema } from '../../support/utils/discountschema';
import { ProductCategory } from '../../support/utils/product';
import { PackingMaterial } from '../../support/utils/packing_material';
import { PackingInstructions } from '../../support/utils/packing_instructions';
import { PackingInstructionsVersion } from '../../support/utils/packing_instructions_version';
import { Builder } from '../../support/utils/builder';
import { getLanguageSpecific, humanReadableNow } from '../../support/utils/utils';
import { DocumentStatusKey } from '../../support/utils/constants';
import { PurchaseOrder, PurchaseOrderLine } from '../../support/utils/purchase_order';

describe('Create Purchase order - complete - reactivate - complete', function() {
  const date = humanReadableNow();
  const productForPackingMaterial = `ProductPackingMaterial ${date}`;
  const packingInstructionsName = `ProductPackingInstrutions ${date}`;
  const productName1 = `ProductTest ${date}`;
  const productName2 = `ProductTest ${date}`;
  const productCategoryName = `ProductCategoryName ${date}`;
  const discountSchemaName = `DiscountSchemaTest ${date}`;
  const priceSystemName = `PriceSystem ${date}`;
  const priceListName = `PriceList ${date}`;
  const priceListVersionName = `PriceListVersion ${date}`;
  const productType = 'Item';
  const vendorName = `Vendor ${date}`;

  it('Create price and product entities to be used in purchase order', function() {
    Builder.createBasicPriceEntities(priceSystemName, priceListVersionName, priceListName, false);
    cy.fixture('discount/discountschema.json').then(discountSchemaJson => {
      Object.assign(new DiscountSchema(), discountSchemaJson)
        .setName(discountSchemaName)
        .apply();
    });
    Builder.createProductWithPriceUsingExistingCategory(
      priceListName,
      productForPackingMaterial,
      productForPackingMaterial,
      productType,
      '24_Gebinde'
    );
    cy.fixture('product/packing_material.json').then(packingMaterialJson => {
      Object.assign(new PackingMaterial(), packingMaterialJson)
        .setName(productForPackingMaterial)
        .setProduct(productForPackingMaterial)
        .apply();
    });
  });
  it('Create packing entities to be used in purchase order', function() {
    cy.fixture('product/packing_instructions.json').then(packingInstructionsJson => {
      Object.assign(new PackingInstructions(), packingInstructionsJson)
        .setName(packingInstructionsName)
        .apply();
    });
    cy.fixture('product/packing_instructions_version.json').then(pivJson => {
      Object.assign(new PackingInstructionsVersion(), pivJson)
        .setName(packingInstructionsName)
        .setPackingInstructions(packingInstructionsName)
        .setPackingMaterial(productForPackingMaterial)
        .apply();
    });
  });
  it('Create product entities to be used in purchase order', function() {
    cy.fixture('product/simple_productCategory.json').then(productCategoryJson => {
      Object.assign(new ProductCategory(), productCategoryJson)
        .setName(productCategoryName)
        .apply();
    });

    Builder.createProductWithPriceAndCUTUAllocationUsingExistingCategory(
      productCategoryName,
      productCategoryName,
      priceListName,
      productName1,
      productName1,
      productType,
      packingInstructionsName
    );
    Builder.createProductWithPriceAndCUTUAllocationUsingExistingCategory(
      productCategoryName,
      productCategoryName,
      priceListName,
      productName2,
      productName2,
      productType,
      packingInstructionsName
    );
  });
  it('Create vendor', function() {
    cy.fixture('sales/simple_vendor.json').then(vendorJson => {
      new BPartner({ ...vendorJson, name: vendorName })
        .setVendorPricingSystem(priceSystemName)
        .setVendorDiscountSchema(discountSchemaName)
        .apply();
    });
    cy.readAllNotifications();
  });
  it('Create a purchase order', function() {
    new PurchaseOrder()
      .setBPartner(vendorName)
      .setPriceSystem(priceSystemName)
      .setPoReference('test')
      .addLine(new PurchaseOrderLine().setProduct(productName1).setQuantity(5))
      .apply();
    cy.completeDocument();
    /**check product name */
    cy.selectNthRow(0)
      .find('.Lookup')
      .find('.lookup-cell')
      .contains(productName1);
    /**check price of product */
    cy.selectNthRow(0)
      .find('.CostPrice')
      .find('.costprice-cell')
      .eq(0)
      .contains('1.23');
    /**check product quantity */
    cy.selectNthRow(0)
      .find('.quantity-cell')
      .contains('5');
    /**purchase order should be completed */
    cy.log('purchase order should be completed');
    cy.fixture('misc/misc_dictionary.json').then(miscDictionary => {
      cy.get('.tag.tag-success').contains(getLanguageSpecific(miscDictionary, DocumentStatusKey.Completed));
    });
    cy.waitUntilProcessIsFinished();
  });
  /**Reactivate purchase order */
  it('Reactivate the purchase order', function() {
    cy.reactivateDocument();
  });
});
