package org.recap.model.jpa;

import org.junit.Test;
import org.recap.BaseTestCaseUT;
import org.recap.model.jpa.*;

import java.util.Arrays;
import java.util.Date;
import java.util.Random;

import static org.junit.Assert.assertNotNull;

/**
 * Created by hemalathas on 20/3/17.
 */
public class BibliographicEntityUT extends BaseTestCaseUT {

    @Test
    public void saveBibSingleHoldingsSingleItem() throws Exception {

        InstitutionEntity institutionEntity = new InstitutionEntity();
        institutionEntity.setInstitutionCode("UC");
        institutionEntity.setInstitutionName("University of Chicago");

        Random random = new Random();
        BibliographicEntity bibliographicEntity = new BibliographicEntity();
        bibliographicEntity.setContent("mock Content".getBytes());
        bibliographicEntity.setCreatedDate(new Date());
        bibliographicEntity.setLastUpdatedDate(new Date());
        bibliographicEntity.setCreatedBy("tst");
        bibliographicEntity.setLastUpdatedBy("tst");
        bibliographicEntity.setOwningInstitutionId(institutionEntity.getId());
        bibliographicEntity.setOwningInstitutionBibId(String.valueOf(random.nextInt()));
        HoldingsEntity holdingsEntity = new HoldingsEntity();
        holdingsEntity.setContent("mock holdings".getBytes());
        holdingsEntity.setCreatedDate(new Date());
        holdingsEntity.setLastUpdatedDate(new Date());
        holdingsEntity.setCreatedBy("tst");
        holdingsEntity.setLastUpdatedBy("tst");
        holdingsEntity.setOwningInstitutionId(1);
        holdingsEntity.setOwningInstitutionHoldingsId(String.valueOf(random.nextInt()));

        ItemEntity itemEntity = new ItemEntity();
        itemEntity.setLastUpdatedDate(new Date());
        itemEntity.setOwningInstitutionItemId(String.valueOf(random.nextInt()));
        itemEntity.setOwningInstitutionId(1);
        itemEntity.setBarcode("4123");
        itemEntity.setCallNumber("x.12321");
        itemEntity.setCollectionGroupId(1);
        itemEntity.setCallNumberType("1");
        itemEntity.setCustomerCode("123");
        itemEntity.setCreatedDate(new Date());
        itemEntity.setCreatedBy("tst");
        itemEntity.setLastUpdatedBy("tst");
        itemEntity.setItemAvailabilityStatusId(1);
        itemEntity.setHoldingsEntities(Arrays.asList(holdingsEntity));

        bibliographicEntity.setHoldingsEntities(Arrays.asList(holdingsEntity));
        bibliographicEntity.setItemEntities(Arrays.asList(itemEntity));
    }

   /* @Test
    public void testBibliographicPK(){
        BibliographicPK bibliographicPK = new BibliographicPK();
        BibliographicPK bibliographicPK1 = new BibliographicPK(1,".b000213654");
        bibliographicPK.setOwningInstitutionBibId(".b000213654");
        bibliographicPK.setOwningInstitutionId(1);
        assertNotNull(bibliographicPK.getOwningInstitutionId());
        assertNotNull(bibliographicPK.getOwningInstitutionBibId());
    }

    @Test
    public void testHoldingsPK(){
        HoldingsPK holdingsPK = new HoldingsPK();
        HoldingsPK holdingsPK1 = new HoldingsPK(1,".b0000000001");
        holdingsPK.setOwningInstitutionHoldingsId(".b0000000001");
        holdingsPK.setOwningInstitutionId(1);
        assertNotNull(holdingsPK.getOwningInstitutionHoldingsId());
        assertNotNull(holdingsPK.getOwningInstitutionId());
    }

    @Test
    public void testItemPK(){
        ItemPK itemPK = new ItemPK();
        itemPK.setOwningInstitutionId(1);
        itemPK.setOwningInstitutionItemId(".b02314364");
        ItemPK itemPK1 = new ItemPK(1,".b02314364");
        assertNotNull(itemPK.getOwningInstitutionId());
        assertNotNull(itemPK.getOwningInstitutionItemId());
    }
*/
}
