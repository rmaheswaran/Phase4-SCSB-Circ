package org.recap.controller;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.recap.BaseTestCaseUT;
import org.recap.request.service.ItemEDDRequestService;
import org.recap.request.service.ItemRequestService;
import org.recap.util.PropertyUtil;
import org.springframework.context.ApplicationContext;

public class OnboardingInstitutionControllerUT extends BaseTestCaseUT {
    
    @InjectMocks
    OnboardingInstitutionController onboardingInstitutionController;
    
    @Mock
    private PropertyUtil propertyUtil;

    @Mock
    private CamelContext camelContext;

    @Mock
    private ApplicationContext applicationContext;
    
    @Mock
    private ItemRequestService itemRequestService;

    @Mock
    private ItemEDDRequestService itemEDDRequestService;

    @Mock
    RouteBuilder routeBuilder;

    @Before
    public void setup(){
        String institutionCode = "PUL";
        Mockito.when(propertyUtil.getPropertyByInstitutionAndKey(institutionCode, "ils.topic.retrieval.request")).thenReturn("scsbactivemq:topic:PUL.RequestT");
        Mockito.when(propertyUtil.getPropertyByInstitutionAndKey(institutionCode, "ils.topic.edd.request")).thenReturn("recap");
        Mockito.when(propertyUtil.getPropertyByInstitutionAndKey(institutionCode, "ils.topic.recall.request")).thenReturn("recap");
    }
    @Test
    public void createTopicsForNewInstitution(){
        String institutionCode = "PUL";
        onboardingInstitutionController.createTopicsForNewInstitution(institutionCode);
    }
    @Test
    public void createQueuesForNewImsLocation(){
        String imsLocationCode = "PUL";
        onboardingInstitutionController.createQueuesForNewImsLocation(imsLocationCode);
    }
}
