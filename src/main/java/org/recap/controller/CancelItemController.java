package org.recap.controller;

import org.apache.commons.lang3.StringUtils;
import org.recap.RecapConstants;
import org.recap.RecapCommonConstants;
import org.recap.ils.model.response.ItemHoldResponse;
import org.recap.ils.model.response.ItemInformationResponse;
import org.recap.model.CancelRequestResponse;
import org.recap.model.jpa.ItemEntity;
import org.recap.model.jpa.ItemRequestInformation;
import org.recap.model.jpa.RequestItemEntity;
import org.recap.model.jpa.RequestStatusEntity;
import org.recap.repository.jpa.RequestItemDetailsRepository;
import org.recap.repository.jpa.RequestItemStatusDetailsRepository;
import org.recap.request.ItemRequestService;
import org.recap.util.CommonUtil;
import org.recap.util.ItemRequestServiceUtil;
import org.recap.util.PropertyUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

/**
 * Created by sudhishk on 31/01/17.
 */
@RestController
@RequestMapping("/cancelRequest")
public class CancelItemController {

    private static final Logger logger = LoggerFactory.getLogger(CancelItemController.class);

    @Autowired
    private RequestItemController requestItemController;

    @Autowired
    private RequestItemDetailsRepository requestItemDetailsRepository;

    @Autowired
    private RequestItemStatusDetailsRepository requestItemStatusDetailsRepository;

    @Autowired
    private ItemRequestService itemRequestService;

    @Autowired
    private ItemRequestServiceUtil itemRequestServiceUtil;

    @Autowired
    private CommonUtil commonUtil;

    @Autowired
    private PropertyUtil propertyUtil;

    /**
     * This is rest service  method, for cancel requested item.
     *
     * @param requestId the request id that already exist in SCSB database.
     * @return CancelRequestResponse custom java object, with information of success and failure.
     * @Exception
     *
     */
    @PostMapping(value = "/cancel", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public CancelRequestResponse cancelRequest(@RequestParam Integer requestId) {
        CancelRequestResponse cancelRequestResponse = new CancelRequestResponse();
        ItemHoldResponse itemCancelHoldResponse = null;
        try {
            Optional<RequestItemEntity> requestItemEntity = requestItemDetailsRepository.findById(requestId);
            if (requestItemEntity.isPresent()) {
                ItemEntity itemEntity = requestItemEntity.get().getItemEntity();
                ItemRequestInformation itemRequestInformation = commonUtil.getItemRequestInformation(itemEntity);
                itemRequestInformation.setRequestingInstitution(requestItemEntity.get().getInstitutionEntity().getInstitutionCode());
                itemRequestInformation.setPatronBarcode(requestItemEntity.get().getPatronId());
                itemRequestInformation.setDeliveryLocation(requestItemEntity.get().getStopCode());
                itemRequestInformation.setRequestId(requestId);

                String requestStatus = requestItemEntity.get().getRequestStatusEntity().getRequestStatusCode();
                ItemInformationResponse itemInformationResponse = (ItemInformationResponse) requestItemController.itemInformation(itemRequestInformation, itemRequestInformation.getRequestingInstitution());
                itemRequestInformation.setBibId(itemInformationResponse.getBibID());
                boolean isRequestTypeRetreivalAndFirstScan = isRequestType(requestItemEntity, RecapCommonConstants.REQUEST_TYPE_RETRIEVAL);
                boolean isRequestTypeRecallAndFirstScan = isRequestType(requestItemEntity, RecapCommonConstants.REQUEST_TYPE_RECALL);
                boolean isRequestTypeEDDAndFirstScan =isRequestType(requestItemEntity, RecapCommonConstants.REQUEST_TYPE_EDD);

                if (requestStatus.equalsIgnoreCase(RecapCommonConstants.REQUEST_STATUS_RETRIEVAL_ORDER_PLACED) || (isRequestTypeRetreivalAndFirstScan)) {
                    itemCancelHoldResponse = processCancelRequest(itemRequestInformation, itemInformationResponse, requestItemEntity.get());
                } else if (requestStatus.equalsIgnoreCase(RecapCommonConstants.REQUEST_STATUS_RECALLED) || (isRequestTypeRecallAndFirstScan)) {
                    itemCancelHoldResponse = processRecall(itemRequestInformation, itemInformationResponse, requestItemEntity.get());
                } else if (requestStatus.equalsIgnoreCase(RecapCommonConstants.REQUEST_STATUS_EDD) || (isRequestTypeEDDAndFirstScan)) {
                    itemCancelHoldResponse = processEDD(requestItemEntity.get());
                } else {
                    itemCancelHoldResponse = new ItemHoldResponse();
                    itemCancelHoldResponse.setSuccess(false);
                    itemCancelHoldResponse.setScreenMessage(RecapConstants.REQUEST_CANCELLATION_NOT_ACTIVE);
                }
            } else {
                itemCancelHoldResponse = new ItemHoldResponse();
                itemCancelHoldResponse.setSuccess(false);
                itemCancelHoldResponse.setScreenMessage(RecapConstants.REQUEST_CANCELLATION_DOES_NOT_EXIST);
            }
        } catch (Exception e) {
            itemCancelHoldResponse = new ItemHoldResponse();
            itemCancelHoldResponse.setSuccess(false);
            itemCancelHoldResponse.setScreenMessage(e.getMessage());
            logger.error(RecapCommonConstants.REQUEST_EXCEPTION, e);
        } finally {
            if (itemCancelHoldResponse == null) {
                itemCancelHoldResponse = new ItemHoldResponse();
            }
            cancelRequestResponse.setSuccess(itemCancelHoldResponse.isSuccess());
            cancelRequestResponse.setScreenMessage(itemCancelHoldResponse.getScreenMessage());
        }
        return cancelRequestResponse;
    }

    boolean isRequestType(Optional<RequestItemEntity> requestItemEntity, String requestType) {
        if(requestItemEntity.isPresent()) {
            return requestItemEntity.get().getRequestTypeEntity().getRequestTypeCode().equalsIgnoreCase(requestType) && requestItemEntity.get().getRequestStatusEntity().getRequestStatusCode().equalsIgnoreCase(RecapConstants.LAS_REFILE_REQUEST_PLACED);
        }
        else return false;
    }

    private ItemHoldResponse processCancelRequest(ItemRequestInformation itemRequestInformation, ItemInformationResponse itemInformationResponse, RequestItemEntity requestItemEntity) {
        ItemHoldResponse itemCancelHoldResponse;
        String checkedOutCirculationStatuses = propertyUtil.getPropertyByInstitutionAndKey(itemRequestInformation.getRequestingInstitution(), "ils.checkedout.circulation.status");
        if (getHoldQueueLength(itemInformationResponse) > 0 || (StringUtils.isNotBlank(checkedOutCirculationStatuses) && StringUtils.containsIgnoreCase(checkedOutCirculationStatuses, itemInformationResponse.getCirculationStatus()))) {
            itemCancelHoldResponse = (ItemHoldResponse) requestItemController.cancelHoldItem(itemRequestInformation, itemRequestInformation.getRequestingInstitution());
            if (itemCancelHoldResponse.isSuccess()) {
                requestItemController.checkinItem(itemRequestInformation, itemRequestInformation.getItemOwningInstitution());
                changeRetrievalToCancelStatus(requestItemEntity, itemCancelHoldResponse);
            } else {
                itemCancelHoldResponse.setSuccess(false);
                itemCancelHoldResponse.setScreenMessage(itemCancelHoldResponse.getScreenMessage());
            }
        } else {
            itemCancelHoldResponse = new ItemHoldResponse();
            changeRetrievalToCancelStatus(requestItemEntity,itemCancelHoldResponse);
        }
        makeItemAvailableForFirstScanCancelRequest(requestItemEntity);
        return itemCancelHoldResponse;
    }

    private void makeItemAvailableForFirstScanCancelRequest(RequestItemEntity requestItemEntity) {
        if (requestItemEntity.getRequestStatusEntity().getRequestStatusCode().equalsIgnoreCase(RecapConstants.LAS_REFILE_REQUEST_PLACED)) {
            commonUtil.rollbackUpdateItemAvailabilityStatus(requestItemEntity.getItemEntity(), RecapConstants.GUEST_USER);
            itemRequestServiceUtil.updateSolrIndex(requestItemEntity.getItemEntity());
        }
    }

    private ItemHoldResponse processRecall(ItemRequestInformation itemRequestInformation, ItemInformationResponse itemInformationResponse, RequestItemEntity requestItemEntity) {
        ItemHoldResponse itemCancelHoldResponse;
        String checkedOutCirculationStatuses = propertyUtil.getPropertyByInstitutionAndKey(itemRequestInformation.getRequestingInstitution(), "ils.checkedout.circulation.status");
        if (getHoldQueueLength(itemInformationResponse) > 0 || (StringUtils.isNotBlank(checkedOutCirculationStatuses) && StringUtils.containsIgnoreCase(checkedOutCirculationStatuses, itemInformationResponse.getCirculationStatus()))) {
            itemRequestInformation.setBibId(itemInformationResponse.getBibID());
            itemCancelHoldResponse = (ItemHoldResponse) requestItemController.cancelHoldItem(itemRequestInformation, itemRequestInformation.getRequestingInstitution());
            if (itemCancelHoldResponse.isSuccess()) {
                changeRecallToCancelStatus(requestItemEntity, itemCancelHoldResponse);
            } else {
                itemCancelHoldResponse.setSuccess(false);
                itemCancelHoldResponse.setScreenMessage(itemCancelHoldResponse.getScreenMessage());
            }
        } else {
            itemCancelHoldResponse = new ItemHoldResponse();
            changeRecallToCancelStatus(requestItemEntity, itemCancelHoldResponse);
        }
        makeItemAvailableForFirstScanCancelRequest(requestItemEntity);
        return itemCancelHoldResponse;
    }

    private ItemHoldResponse processEDD(RequestItemEntity requestItemEntity) {
        ItemHoldResponse itemCancelHoldResponse = new ItemHoldResponse();
        saveRequestAndChangeLog(requestItemEntity);
        itemCancelHoldResponse.setSuccess(true);
        itemCancelHoldResponse.setScreenMessage(RecapConstants.REQUEST_CANCELLATION_EDD_SUCCCESS);
        sendEmail(requestItemEntity.getItemEntity().getCustomerCode(), requestItemEntity.getItemEntity().getBarcode(), requestItemEntity.getItemEntity().getImsLocationEntity().getImsLocationCode(), requestItemEntity.getPatronId());
        makeItemAvailableForFirstScanCancelRequest(requestItemEntity);
        return itemCancelHoldResponse;
    }

    private void saveRequestAndChangeLog(RequestItemEntity requestItemEntity) {
        RequestStatusEntity requestStatusEntity = requestItemStatusDetailsRepository.findByRequestStatusCode(RecapCommonConstants.REQUEST_STATUS_CANCELED);
        requestItemEntity.setRequestStatusId(requestStatusEntity.getId());
        requestItemEntity.setLastUpdatedDate(new Date());
        requestItemEntity.setNotes(appendCancelMessageToNotes(requestItemEntity));
        RequestItemEntity savedRequestItemEntity = requestItemDetailsRepository.save(requestItemEntity);
        itemRequestService.saveItemChangeLogEntity(savedRequestItemEntity.getId(), RecapConstants.GUEST_USER, RecapConstants.REQUEST_ITEM_CANCEL_ITEM_AVAILABILITY_STATUS, RecapCommonConstants.REQUEST_STATUS_CANCELED + savedRequestItemEntity.getItemId());
    }

    private int getHoldQueueLength(ItemInformationResponse itemInformationResponse) {
        int iholdQueue = 0;
        if (itemInformationResponse.getHoldQueueLength().trim().length() > 0) {
            iholdQueue = Integer.parseInt(itemInformationResponse.getHoldQueueLength());
        }
        return iholdQueue;
    }

    private void sendEmail(String customerCode, String itemBarcode, String imsLocationCode, String patronBarcode) {
        itemRequestService.getEmailService().sendEmail(customerCode, itemBarcode, imsLocationCode, RecapConstants.REQUEST_CANCELLED_NO_REFILED, patronBarcode, RecapConstants.GFA, RecapConstants.REQUEST_CANCELLED_SUBJECT);
    }

    private void changeRetrievalToCancelStatus(RequestItemEntity requestItemEntity, ItemHoldResponse itemCancelHoldResponse) {
        saveRequestAndChangeLog(requestItemEntity);
        itemCancelHoldResponse.setSuccess(true);
        itemCancelHoldResponse.setScreenMessage(RecapConstants.REQUEST_CANCELLATION_SUCCCESS);
        logger.info("Send Mail");
        sendEmail(requestItemEntity.getItemEntity().getCustomerCode(), requestItemEntity.getItemEntity().getBarcode(), requestItemEntity.getItemEntity().getImsLocationEntity().getImsLocationCode(), requestItemEntity.getPatronId());
        logger.info("Send Mail Done");
    }

    private void changeRecallToCancelStatus(RequestItemEntity requestItemEntity, ItemHoldResponse itemCancelHoldResponse) {
        saveRequestAndChangeLog(requestItemEntity);
        itemCancelHoldResponse.setSuccess(true);
        itemCancelHoldResponse.setScreenMessage(RecapConstants.RECALL_CANCELLATION_SUCCCESS);
    }

    private String appendCancelMessageToNotes(RequestItemEntity requestItemEntity) {
        DateFormat cancelRequestDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return requestItemEntity.getNotes() + "\nCancel requested ["+cancelRequestDateFormat.format(new Date())+"]";
    }
}
