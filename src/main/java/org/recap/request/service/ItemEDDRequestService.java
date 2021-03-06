package org.recap.request.service;

import org.apache.camel.Exchange;
import org.apache.commons.lang3.StringUtils;
import org.recap.ScsbCommonConstants;
import org.recap.ScsbConstants;
import org.recap.controller.RequestItemController;
import org.recap.model.response.ItemCheckoutResponse;
import org.recap.model.response.ItemInformationResponse;
import org.recap.model.jpa.GenericPatronEntity;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.request.ItemRequestInformation;
import org.recap.model.search.SearchResultRow;
import org.recap.repository.jpa.GenericPatronDetailsRepository;
import org.recap.repository.jpa.ItemDetailsRepository;
import org.recap.repository.jpa.RequestItemDetailsRepository;
import org.recap.repository.jpa.RequestTypeDetailsRepository;
import org.recap.request.util.ItemRequestServiceUtil;
import org.recap.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Created by sudhishk on 1/12/16.
 */
@Component
public class ItemEDDRequestService {

    private static final Logger logger = LoggerFactory.getLogger(ItemEDDRequestService.class);

    @Autowired
    private ItemDetailsRepository itemDetailsRepository;

    @Autowired
    private RequestTypeDetailsRepository requestTypeDetailsRepository;

    @Autowired
    private ItemRequestService itemRequestService;

    @Autowired
    private ItemRequestServiceUtil itemRequestServiceUtil;

    @Autowired
    private RequestItemController requestItemController;

    @Autowired
    private RequestItemDetailsRepository requestItemDetailsRepository;

    @Autowired
    private GenericPatronDetailsRepository genericPatronDetailsRepository;

    @Autowired
    private PropertyUtil propertyUtil;

    /**
     * Gets item details repository.
     *
     * @return the item details repository
     */
    public ItemDetailsRepository getItemDetailsRepository() {
        return itemDetailsRepository;
    }

    /**
     * Gets request type details repository.
     *
     * @return the request type details repository
     */
    public RequestTypeDetailsRepository getRequestTypeDetailsRepository() {
        return requestTypeDetailsRepository;
    }

    /**
     * Gets item request service.
     *
     * @return the item request service
     */
    public ItemRequestService getItemRequestService() {
        return itemRequestService;
    }

    /**
     * Gets item information response.
     *
     * @return the item information response
     */
    public ItemInformationResponse getItemInformationResponse() {
        return new ItemInformationResponse();
    }

    /**
     * Edd request item item information response.
     *
     * @param itemRequestInfo the item request info
     * @param exchange        the exchange
     * @return the item information response
     */
    public ItemInformationResponse eddRequestItem(ItemRequestInformation itemRequestInfo, Exchange exchange) {

        List<ItemEntity> itemEntities;
        ItemEntity itemEntity = null;
        ItemInformationResponse itemResponseInformation = getItemInformationResponse();
        Integer requestId;
        try {
            itemEntities = getItemDetailsRepository().findByBarcodeIn(itemRequestInfo.getItemBarcodes());
            if (itemEntities != null && !itemEntities.isEmpty()) {
                logger.info("Item Exists in SCSB Database");
                itemEntity = itemEntities.get(0);
                if (itemEntity.getBibliographicEntities().get(0).getOwningInstitutionBibId().trim().length() <= 0) {
                    itemRequestInfo.setBibId(itemEntity.getBibliographicEntities().get(0).getOwningInstitutionBibId());
                }
                SearchResultRow searchResultRow = getItemRequestService().searchRecords(itemEntity);

                itemRequestInfo.setItemOwningInstitution(itemEntity.getInstitutionEntity().getInstitutionCode());
                itemRequestInfo.setImsLocationCode(itemEntity.getImsLocationEntity().getImsLocationCode());
                itemRequestInfo.setTitleIdentifier(getItemRequestService().removeDiacritical(searchResultRow.getTitle().replaceAll("[^\\x00-\\x7F]", "?")));
                itemRequestInfo.setItemAuthor(getItemRequestService().removeDiacritical(searchResultRow.getAuthor()));
                itemRequestInfo.setCustomerCode(itemEntity.getCustomerCode());
                // Save user Notes to be sent to LAS
                itemRequestInfo.setEddNotes(itemRequestInfo.getRequestNotes());
                // Add EDD Information to notes to be saved in database
                itemRequestInfo.setRequestNotes(getNotes(itemRequestInfo));
                boolean isItemStatusAvailable;
                synchronized (this) {
                    // Change Item Availability
                    isItemStatusAvailable = getItemRequestService().updateItemAvailabilityStatus(itemEntities, itemRequestInfo.getUsername());
                }
                requestId = getItemRequestService().updateRecapRequestItem(itemRequestInfo, itemEntity, ScsbConstants.REQUEST_STATUS_PROCESSING);
                itemRequestInfo.setRequestId(requestId);
                itemResponseInformation.setRequestId(requestId);

                if (requestId == 0) {
                    itemResponseInformation.setScreenMessage(ScsbConstants.INTERNAL_ERROR_DURING_REQUEST);
                    itemResponseInformation.setSuccess(false);
                    getItemRequestService().rollbackUpdateItemAvailabilityStatus(itemEntity, ScsbConstants.GUEST_USER);
                } else if (!isItemStatusAvailable) {
                    itemResponseInformation.setScreenMessage(ScsbConstants.RETRIEVAL_NOT_FOR_UNAVAILABLE_ITEM);
                    itemResponseInformation.setSuccess(false);
                } else {
                    // Process
                    String requestInfoPatronBarcode = itemRequestInfo.getPatronBarcode();
                    if (getItemRequestService().getGfaLasService().isUseQueueLasCall(itemRequestInfo.getImsLocationCode())) {
                        getItemRequestService().updateRecapRequestItem(itemRequestInfo, itemEntity, ScsbConstants.REQUEST_STATUS_PENDING);
                    }
                    itemResponseInformation.setItemId(itemEntity.getId());

                    if (itemRequestInfo.isOwningInstitutionItem()) {
                        String useGenericPatronEddForSelf = propertyUtil.getPropertyByInstitutionAndKey(itemRequestInfo.getRequestingInstitution(), "use.generic.patron.edd.self");
                        if (Boolean.TRUE.toString().equalsIgnoreCase(useGenericPatronEddForSelf)) {
                            try {
                                itemRequestInfo.setPatronBarcode(getPatronIdForOwningInstitutionOnEdd(itemRequestInfo.getItemOwningInstitution()));
                            } catch (Exception e) {
                                logger.error(ScsbCommonConstants.REQUEST_EXCEPTION, e);
                                itemResponseInformation.setScreenMessage(ScsbConstants.GENERIC_PATRON_NOT_FOUND_ERROR);
                                itemResponseInformation.setSuccess(false);
                            }
                        }
                    } else {
                        String useGenericPatronEddForCrossInst = propertyUtil.getPropertyByInstitutionAndKey(itemRequestInfo.getRequestingInstitution(), "use.generic.patron.edd.cross");
                        if (Boolean.TRUE.toString().equalsIgnoreCase(useGenericPatronEddForCrossInst)) {
                            try {
                                itemRequestInfo.setPatronBarcode(itemRequestServiceUtil.getPatronIdBorrowingInstitution(itemRequestInfo.getRequestingInstitution(), itemRequestInfo.getItemOwningInstitution(), ScsbCommonConstants.REQUEST_TYPE_EDD));
                            } catch (Exception e) {
                                logger.error(ScsbCommonConstants.REQUEST_EXCEPTION, e);
                                itemResponseInformation.setScreenMessage(ScsbConstants.GENERIC_PATRON_NOT_FOUND_ERROR);
                                itemResponseInformation.setSuccess(false);
                            }
                        }
                    }

                    itemResponseInformation = getItemRequestService().updateGFA(itemRequestInfo, itemResponseInformation);
                    if (itemResponseInformation.isRequestTypeForScheduledOnWO()) {
                        logger.info("EDD Request Received on first scan");
                        requestId = getItemRequestService().updateRecapRequestItem(itemRequestInfo, itemEntity, ScsbConstants.LAS_REFILE_REQUEST_PLACED);
                        logger.info("Updated EDD request id {} on first scan", requestId);
                    }
                    if (!itemResponseInformation.isSuccess()) {
                        getItemRequestService().rollbackUpdateItemAvailabilityStatus(itemEntity, ScsbConstants.GUEST_USER);
                    } else {
                        logger.info("Patron and Institution info before CheckOut Call in EDD : patron - {} , institution - {}", itemRequestInfo.getPatronBarcode(), itemRequestInfo.getItemOwningInstitution());
                        ItemCheckoutResponse itemCheckoutResponse = (ItemCheckoutResponse) requestItemController.checkoutItem(itemRequestInfo, itemRequestInfo.getItemOwningInstitution());
                        if (itemCheckoutResponse.isSuccess()) {
                            itemResponseInformation.setEddSuccessResponseScreenMsg(itemCheckoutResponse.getScreenMessage());
                        } else {
                            itemResponseInformation.setEddFailureResponseScreenMsg(itemCheckoutResponse.getScreenMessage());
                        }
                    }
                    itemResponseInformation.setPatronBarcode(requestInfoPatronBarcode);
                }
            } else {
                itemResponseInformation.setScreenMessage(ScsbConstants.WRONG_ITEM_BARCODE);
                itemResponseInformation.setSuccess(false);
            }
            logger.info("Finish Processing");
            itemResponseInformation.setItemOwningInstitution(itemRequestInfo.getItemOwningInstitution());
            itemResponseInformation.setDueDate(itemRequestInfo.getExpirationDate());
            itemResponseInformation.setRequestingInstitution(itemRequestInfo.getRequestingInstitution());
            itemResponseInformation.setTitleIdentifier(itemRequestInfo.getTitleIdentifier());
            itemResponseInformation.setBibID(itemRequestInfo.getBibId());
            itemResponseInformation.setItemBarcode(itemRequestInfo.getItemBarcodes().get(0));
            itemResponseInformation.setRequestType(itemRequestInfo.getRequestType());
            itemResponseInformation.setEmailAddress(itemRequestInfo.getEmailAddress());
            itemResponseInformation.setDeliveryLocation(itemRequestInfo.getDeliveryLocation());
            itemResponseInformation.setUsername(itemRequestInfo.getUsername());
            itemResponseInformation.setImsLocationCode(itemRequestInfo.getImsLocationCode());
            if (!itemResponseInformation.isSuccess()) {
                itemResponseInformation.setRequestNotes(itemRequestInfo.getRequestNotes() + "\n" + ScsbConstants.REQUEST_SCSB_EXCEPTION + itemResponseInformation.getScreenMessage());
                getItemRequestService().updateChangesToDb(itemResponseInformation, ScsbCommonConstants.REQUEST_TYPE_EDD + "-" + itemResponseInformation.getRequestingInstitution());
            } else {
                itemResponseInformation.setRequestNotes(itemRequestInfo.getRequestNotes());
                if (itemEntity != null) {
                    itemRequestServiceUtil.updateSolrIndex(itemEntity);
                }
            }
            // Update Topics
            getItemRequestService().sendMessageToTopic(itemRequestInfo.getRequestingInstitution(), itemRequestInfo.getRequestType(), itemResponseInformation, exchange);
        } catch (RestClientException ex) {
            logger.error(ScsbCommonConstants.REQUEST_EXCEPTION_REST, ex);
        } catch (Exception ex) {
            logger.error(ScsbCommonConstants.REQUEST_EXCEPTION, ex);
        }
        return itemResponseInformation;
    }

    private String getNotes(ItemRequestInformation itemRequestInfo) {
        String notes = "";
        if (!StringUtils.isBlank(itemRequestInfo.getRequestNotes())) {
            notes = String.format("User: %s", itemRequestInfo.getRequestNotes().replace("\n", " "));
        }
        notes += String.format("\n\nStart Page: %s \nEnd Page: %s \nVolume Number: %s \nIssue: %s \nArticle Author: %s \nArticle/Chapter Title: %s ", itemRequestInfo.getStartPage(), itemRequestInfo.getEndPage(), itemRequestInfo.getVolume(), itemRequestInfo.getIssue(), itemRequestInfo.getAuthor(), itemRequestInfo.getChapterTitle());
        return notes;
    }

    public String getPatronIdForOwningInstitutionOnEdd(String owningInstitution) {
        GenericPatronEntity genericPatronEntity = genericPatronDetailsRepository.findByRequestingInstitutionCodeAndItemOwningInstitutionCode(owningInstitution, owningInstitution);
        return genericPatronEntity.getEddGenericPatron();
    }

}
