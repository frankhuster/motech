package org.motechproject.mds.web.service.impl;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.motechproject.commons.date.model.Time;
import org.motechproject.commons.date.util.DateUtil;
import org.motechproject.mds.dto.EntityDto;
import org.motechproject.mds.dto.FieldDto;
import org.motechproject.mds.dto.LookupDto;
import org.motechproject.mds.dto.TypeDto;
import org.motechproject.mds.ex.entity.EntityInstancesNonEditableException;
import org.motechproject.mds.ex.entity.EntityNotFoundException;
import org.motechproject.mds.ex.object.ObjectNotFoundException;
import org.motechproject.mds.ex.object.ObjectUpdateException;
import org.motechproject.mds.query.QueryParams;
import org.motechproject.mds.service.DefaultMotechDataService;
import org.motechproject.mds.service.EntityService;
import org.motechproject.mds.service.MotechDataService;
import org.motechproject.mds.service.TrashService;
import org.motechproject.mds.util.ClassName;
import org.motechproject.mds.util.Constants;
import org.motechproject.mds.util.MDSClassLoader;
import org.motechproject.mds.util.Order;
import org.motechproject.mds.util.SecurityMode;
import org.motechproject.mds.web.FieldTestHelper;
import org.motechproject.mds.web.domain.EntityRecord;
import org.motechproject.mds.web.domain.FieldRecord;
import org.motechproject.mds.web.domain.Records;
import org.motechproject.mds.web.service.InstanceService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.motechproject.mds.ex.object.SecurityException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ch.lambdaj.Lambda.extract;
import static ch.lambdaj.Lambda.on;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class InstanceServiceTest {

    private static final long ENTITY_ID = 11;
    private static final long ANOTHER_ENTITY_ID = 12;
    private static final long INSTANCE_ID = 4;

    @InjectMocks
    private InstanceService instanceService = new InstanceServiceImpl();

    @Mock
    private EntityService entityService;

    @Mock
    private EntityDto entity;

    @Mock
    private MotechDataService motechDataService;

    @Mock
    private BundleContext bundleContext;

    @Mock
    private ServiceReference serviceReference;

    @Mock
    private TrashService trashService;

    @Mock
    private MotechDataService serviceForAnotherSample;

    @Before
    public void setUp() {
        when(entity.getClassName()).thenReturn(TestSample.class.getName());
        when(entity.getId()).thenReturn(ENTITY_ID);
        when(bundleContext.getBundles()).thenReturn(new Bundle[0]);
    }

    @Test
    public void shouldReturnNewInstances() {
        mockSampleFields();
        mockEntity();

        EntityRecord record = instanceService.newInstance(ENTITY_ID);

        assertNotNull(record);
        assertEquals(Long.valueOf(ENTITY_ID), record.getEntitySchemaId());
        assertNull(record.getId());

        List<FieldRecord> fieldRecords = record.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("Default", 7, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));
    }

    @Test
    public void shouldReturnEntityInstance() {
        mockDataService();
        mockSampleFields();
        mockEntity();
        when(motechDataService.retrieve("id", INSTANCE_ID))
                .thenReturn(new TestSample("Hello world", 99));

        EntityRecord record = instanceService.getEntityInstance(ENTITY_ID, INSTANCE_ID);

        assertNotNull(record);
        assertEquals(Long.valueOf(ENTITY_ID), record.getEntitySchemaId());
        assertEquals(Long.valueOf(INSTANCE_ID), record.getId());

        List<FieldRecord> fieldRecords = record.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("Hello world", 99, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));
    }

    @Test
    public void shouldReturnInstancesFromTrash() {
        mockDataService();
        mockSampleFields();
        mockEntity();
        QueryParams queryParams = new QueryParams(1, 10, null);
        when(trashService.getInstancesFromTrash(anyString(), eq(queryParams))).thenReturn(sampleCollection());

        List<EntityRecord> records = instanceService.getTrashRecords(ENTITY_ID, queryParams);
        verify(trashService).getInstancesFromTrash(anyString(), eq(queryParams));
        assertNotNull(records);
        assertEquals(records.size(), 1);

        //Make sure all fields that were in the instance are still available
        assertEquals(records.get(0).getFields().size(), 5);
    }

    @Test(expected = ObjectNotFoundException.class)
    public void shouldThrowObjectNotFoundExceptionWhenNoInstanceFound() {
        mockDataService();
        mockSampleFields();
        mockEntity();

        instanceService.getEntityInstance(ENTITY_ID, INSTANCE_ID);
    }

    @Test(expected = EntityNotFoundException.class)
    public void shouldThrowEntityNotFoundException() {
        instanceService.getEntityInstance(ENTITY_ID, INSTANCE_ID);
    }

    @Test
    public void shouldCreateNewInstances() throws ClassNotFoundException {
        testUpdateCreate(false);
    }

    @Test
    public void shouldUpdateObjectInstance() throws ClassNotFoundException {
        testUpdateCreate(true);
    }

    @Test
    public void shouldCountAllEntities() {
        mockSampleFields();
        mockDataService();
        mockEntity();

        when(motechDataService.count()).thenReturn(56L);

        assertEquals(56L, instanceService.countRecords(ENTITY_ID));
    }

    @Test
    public void shouldRetrieveInstancesBasedOnASingleReturnLookup() {
        mockSampleFields();
        mockEntity();
        mockLookups();
        mockLookupService();

        Map<String, Object> lookupMap = new HashMap<>();
        lookupMap.put("strField", TestDataService.LOOKUP_1_EXPECTED_PARAM);

        List<EntityRecord> result = instanceService.getEntityRecordsFromLookup(ENTITY_ID, TestDataService.LOOKUP_1_NAME,
                lookupMap, queryParams());

        assertNotNull(result);
        assertEquals(1, result.size());

        EntityRecord entityRecord = result.get(0);
        assertEquals(Long.valueOf(ENTITY_ID), entityRecord.getEntitySchemaId());

        List<FieldRecord> fieldRecords = entityRecord.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("strField", 6, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));
    }

    @Test
    public void shouldRetrieveInstancesBasedOnAMultiReturnLookup() {
        mockSampleFields();
        mockEntity();
        mockLookups();
        mockLookupService();

        Map<String, Object> lookupMap = new HashMap<>();
        lookupMap.put("strField", TestDataService.LOOKUP_2_EXPECTED_PARAM);

        List<EntityRecord> result = instanceService.getEntityRecordsFromLookup(ENTITY_ID, TestDataService.LOOKUP_2_NAME,
                lookupMap, queryParams());

        assertNotNull(result);
        assertEquals(2, result.size());

        EntityRecord entityRecord = result.get(0);
        assertEquals(Long.valueOf(ENTITY_ID), entityRecord.getEntitySchemaId());

        List<FieldRecord> fieldRecords = entityRecord.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("one", 1, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));

        entityRecord = result.get(1);
        assertEquals(Long.valueOf(ENTITY_ID), entityRecord.getEntitySchemaId());

        fieldRecords = entityRecord.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("two", 2, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));
    }

    @Test
    public void shouldHandleNullParamsForLookups() {
        mockSampleFields();
        mockEntity();
        mockLookups();
        mockLookupService();

        Map<String, Object> lookupMap = new HashMap<>();
        lookupMap.put("dtField", null);

        List<EntityRecord> result = instanceService.getEntityRecordsFromLookup(ENTITY_ID,
                TestDataService.NULL_EXPECTING_LOOKUP_NAME, lookupMap, queryParams());

        assertNotNull(result);
        assertEquals(2, result.size());

        EntityRecord entityRecord = result.get(0);
        assertEquals(Long.valueOf(ENTITY_ID), entityRecord.getEntitySchemaId());

        List<FieldRecord> fieldRecords = entityRecord.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("three", 3, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));

        entityRecord = result.get(1);
        assertEquals(Long.valueOf(ENTITY_ID), entityRecord.getEntitySchemaId());

        fieldRecords = entityRecord.getFields();
        assertCommonFieldRecordFields(fieldRecords);
        assertEquals(asList("four", 4, null, null, null),
                extract(fieldRecords, on(FieldRecord.class).getValue()));
    }

    @Test
    public void shouldRevertInstanceFromTrash() {
        mockSampleFields();
        mockEntity();
        mockLookups();
        mockLookupService();

        instanceService.revertInstanceFromTrash(ENTITY_ID, INSTANCE_ID);
    }

    @Test
    public void shouldCountForLookup() {
        mockSampleFields();
        mockEntity();
        mockLookups();
        mockLookupService();

        Map<String, Object> lookupMap = new HashMap<>();
        lookupMap.put("strField", TestDataService.LOOKUP_1_EXPECTED_PARAM);

        long count = instanceService.countRecordsByLookup(ENTITY_ID, TestDataService.LOOKUP_1_NAME, lookupMap);
        assertEquals(1L, count);

        lookupMap.put("strField", TestDataService.LOOKUP_2_EXPECTED_PARAM);

        count = instanceService.countRecordsByLookup(ENTITY_ID, TestDataService.LOOKUP_2_NAME, lookupMap);
        assertEquals(22L, count);

        lookupMap.clear();
        lookupMap.put("dtField", null);

        count = instanceService.countRecordsByLookup(ENTITY_ID, TestDataService.NULL_EXPECTING_LOOKUP_NAME, lookupMap);
        assertEquals(2, count);
    }

    @Test
    public void shouldUseCorrectClassLoaderWhenCreatingInstances() throws ClassNotFoundException {
        mockSampleFields();
        mockEntity();
        mockDataService();

        Bundle ddeBundle = mock(Bundle.class);
        Bundle entitiesBundle = mock(Bundle.class);
        when(bundleContext.getBundles()).thenReturn(new Bundle[]{ddeBundle, entitiesBundle});

        when(entity.getBundleSymbolicName()).thenReturn("org.motechproject.test");

        when(entitiesBundle.getSymbolicName()).thenReturn(Constants.BundleNames.MDS_ENTITIES_SYMBOLIC_NAME);
        when(ddeBundle.getSymbolicName()).thenReturn("org.motechproject.test");

        Class testClass = TestSample.class;
        when(entitiesBundle.loadClass(testClass.getName())).thenReturn(testClass);
        when(ddeBundle.loadClass(testClass.getName())).thenReturn(testClass);

        EntityRecord entityRecord = new EntityRecord(null, ENTITY_ID, Collections.<FieldRecord>emptyList());

        when(entity.isDDE()).thenReturn(true);
        instanceService.saveInstance(entityRecord);
        verify(ddeBundle).loadClass(TestSample.class.getName());

        when(entity.isDDE()).thenReturn(false);
        instanceService.saveInstance(entityRecord);
        verify(entitiesBundle).loadClass(TestSample.class.getName());

        verify(motechDataService, times(2)).create(any(TestSample.class));
    }

    @Test
    public void shouldUpdateRelatedFields() {
        TestSample test1 = new TestSample("someString", 4);
        TestSample test2 = new TestSample("otherString", 5);
        TestSample test3 = new TestSample("sample", 6);

        Map propertiesMap = new HashMap();
        propertiesMap.put("id", 6);

        List<FieldRecord> fieldRecords = asList(
                FieldTestHelper.fieldRecord("title", String.class.getName(), "String field", "Default"),
                FieldTestHelper.fieldRecord(TypeDto.ONE_TO_MANY_RELATIONSHIP, "testSamples", "Related field", buildRelatedRecord()),
                FieldTestHelper.fieldRecord(TypeDto.ONE_TO_ONE_RELATIONSHIP, "testSample", "Other Related field", propertiesMap)
        );

        EntityRecord entityRecord = new EntityRecord(null, ANOTHER_ENTITY_ID, fieldRecords);

        mockSampleFields();
        mockDataService();
        mockEntity();

        EntityDto entityWithRelatedField = mock(EntityDto.class);
        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityWithRelatedField);
        when(entityWithRelatedField.getClassName()).thenReturn(AnotherSample.class.getName());

        ServiceReference serviceReferenceForClassWithRelatedField = mock(ServiceReference.class);
        MotechDataService serviceForClassWithRelatedField = mock(MotechDataService.class);
        when(bundleContext.getServiceReference(ClassName.getInterfaceName(AnotherSample.class.getName())))
                .thenReturn(serviceReferenceForClassWithRelatedField);
        when(bundleContext.getService(serviceReferenceForClassWithRelatedField)).thenReturn(serviceForClassWithRelatedField);
        when(motechDataService.findById(4l)).thenReturn(test1);
        when(motechDataService.findById(5l)).thenReturn(test2);
        when(motechDataService.findById(6l)).thenReturn(test3);

        when(entityService.getEntityFields(ANOTHER_ENTITY_ID)).thenReturn(asList(
                FieldTestHelper.fieldDto(5L, "title", String.class.getName(), "String field", "Default"),
                FieldTestHelper.fieldDto(6L, "testSamples", TypeDto.ONE_TO_MANY_RELATIONSHIP.getTypeClass(), "Related field", null)
        ));

        ArgumentCaptor<AnotherSample> captor = ArgumentCaptor.forClass(AnotherSample.class);
        instanceService.saveInstance(entityRecord, null);

        verify(serviceForClassWithRelatedField).create(captor.capture());
        AnotherSample capturedValue = captor.getValue();
        assertEquals(capturedValue.getTestSample(), test3);
        assertEquals(capturedValue.getTestSamples().size(), 2);
        assertEquals(capturedValue.getTitle(), "Default");
        assertTrue(capturedValue.getTestSamples().contains(test1));
        assertFalse(capturedValue.getTestSamples().contains(test3));
        assertTrue(capturedValue.getTestSamples().contains(test2));
    }

    @Test
    public void shouldRevertHistoryInstanceWithRelatedFields() {
        mockAnotherEntity();

        final long OBJECT_ID = 8;
        final long OBJECT2_ID = 9;
        final long HISTORY_OBJECT_ID = 10;
        final long HISTORY_OBJECT2_ID = 11;

        TestClass object = new TestClass(OBJECT_ID);
        TestClass object2 = new TestClass(OBJECT2_ID);

        TestClass__History historyObject = new TestClass__History(HISTORY_OBJECT_ID, OBJECT_ID);
        TestClass__History historyObject2 = new TestClass__History(HISTORY_OBJECT2_ID, OBJECT2_ID);

        List<FieldRecord> fieldRecords = asList(
                FieldTestHelper.fieldRecord(TypeDto.ONE_TO_MANY_RELATIONSHIP, "testClasses", "Related field",
                        new HashSet<>(Arrays.asList(historyObject, historyObject2))),
                FieldTestHelper.fieldRecord(TypeDto.ONE_TO_ONE_RELATIONSHIP, "testClass", "Other Related field", historyObject)
        );

        EntityRecord entityRecord = new EntityRecord(null, ANOTHER_ENTITY_ID, fieldRecords);

        ServiceReference serviceReferenceForTestClass = mock(ServiceReference.class);
        MotechDataService serviceForTestClass = mock(MotechDataService.class);
        when(bundleContext.getServiceReference(ClassName.getInterfaceName(TestClass.class.getName())))
                .thenReturn(serviceReferenceForTestClass);
        when(bundleContext.getService(serviceReferenceForTestClass)).thenReturn(serviceForTestClass);

        when(serviceForTestClass.findById(OBJECT_ID)).thenReturn(object);
        when(serviceForTestClass.findById(OBJECT2_ID)).thenReturn(object2);
        ArgumentCaptor<AnotherSample> captor = ArgumentCaptor.forClass(AnotherSample.class);

        instanceService.saveInstance(entityRecord);

        verify(serviceForAnotherSample).create(captor.capture());
        AnotherSample capturedValue = captor.getValue();
        assertEquals(object, capturedValue.getTestClass());
        assertEquals(2, capturedValue.getTestClasses().size());
        assertTrue(capturedValue.getTestClasses().contains(object));
        assertTrue(capturedValue.getTestClasses().contains(object2));
    }

    private void mockAnotherEntity() {
        EntityDto entityWithRelatedField = mock(EntityDto.class);
        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityWithRelatedField);
        when(entityWithRelatedField.getClassName()).thenReturn(AnotherSample.class.getName());
        when(entityWithRelatedField.getName()).thenReturn(AnotherSample.class.getSimpleName());

        ServiceReference serviceReferenceForAnotherSample = mock(ServiceReference.class);
        when(bundleContext.getServiceReference(ClassName.getInterfaceName(AnotherSample.class.getName())))
                .thenReturn(serviceReferenceForAnotherSample);
        when(bundleContext.getService(serviceReferenceForAnotherSample)).thenReturn(serviceForAnotherSample);
    }

    @Test(expected = EntityInstancesNonEditableException.class)
    public void shouldThrowExceptionWhileSavingInstanceInNonEditableEntity() {
        EntityDto nonEditableEntity = new EntityDto();
        nonEditableEntity.setNonEditable(true);
        EntityRecord entityRecord = new EntityRecord(null, ANOTHER_ENTITY_ID, new ArrayList<FieldRecord>());

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(nonEditableEntity);

        instanceService.saveInstance(entityRecord);
    }

    @Test(expected = EntityInstancesNonEditableException.class)
    public void shouldThrowExceptionWhileDeletingInstanceInNonEditableEntity() {
        EntityDto nonEditableEntity = new EntityDto();
        nonEditableEntity.setNonEditable(true);

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(nonEditableEntity);

        instanceService.deleteInstance(ANOTHER_ENTITY_ID, INSTANCE_ID);
    }

    @Test(expected = SecurityException.class)
    public void shouldThrowExceptionWhileSavingInstanceWithReadOnlyPermission() {
        EntityDto entityDto = new EntityDto();
        entityDto.setReadOnlySecurityMode(SecurityMode.EVERYONE);
        entityDto.setSecurityMode(SecurityMode.NO_ACCESS);

        EntityRecord entityRecord = new EntityRecord(null, ANOTHER_ENTITY_ID, new ArrayList<FieldRecord>());

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityDto);

        instanceService.saveInstance(entityRecord);
    }

    @Test(expected = SecurityException.class)
    public void shouldThrowExceptionWhileReadingInstanceWithoutAnyPermission() {
        EntityDto entityDto = new EntityDto();
        entityDto.setReadOnlySecurityMode(SecurityMode.NO_ACCESS);
        entityDto.setSecurityMode(SecurityMode.NO_ACCESS);

        EntityRecord entityRecord = new EntityRecord(ANOTHER_ENTITY_ID, null, new ArrayList<FieldRecord>());

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityDto);

        instanceService.getEntityRecords(entityRecord.getId());
    }

    @Test
    public void shouldAcceptUserWithReadAccessPermissionWhileReadingInstance() {
        EntityDto entityDto = new EntityDto();
        entityDto.setReadOnlySecurityMode(SecurityMode.EVERYONE);
        entityDto.setSecurityMode(SecurityMode.NO_ACCESS);
        entityDto.setClassName(TestSample.class.getName());

        EntityRecord entityRecord = new EntityRecord(ANOTHER_ENTITY_ID, null, new ArrayList<FieldRecord>());

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityDto);

        mockDataService();
        instanceService.getEntityRecords(entityRecord.getId());

        verify(entityService).getEntityFields(ANOTHER_ENTITY_ID);
    }

    @Test
    public void shouldAcceptUserWithRegularAccessPermissionWhileReadingInstance() {
        EntityDto entityDto = new EntityDto();
        entityDto.setReadOnlySecurityMode(SecurityMode.NO_ACCESS);
        entityDto.setSecurityMode(SecurityMode.EVERYONE);
        entityDto.setClassName(TestSample.class.getName());

        EntityRecord entityRecord = new EntityRecord(ANOTHER_ENTITY_ID, null, new ArrayList<FieldRecord>());

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityDto);

        mockDataService();
        instanceService.getEntityRecords(entityRecord.getId());

        verify(entityService).getEntityFields(ANOTHER_ENTITY_ID);
    }

    @Test
    public void shouldAcceptUserWithNoPermissionWhileReadingInstanceWithNoSecurityMode() {
        EntityDto entityDto = new EntityDto();
        entityDto.setReadOnlySecurityMode(null);
        entityDto.setSecurityMode(null);
        entityDto.setClassName(TestSample.class.getName());

        EntityRecord entityRecord = new EntityRecord(ANOTHER_ENTITY_ID, null, new ArrayList<FieldRecord>());

        when(entityService.getEntity(ANOTHER_ENTITY_ID)).thenReturn(entityDto);

        mockDataService();
        instanceService.getEntityRecords(entityRecord.getId());

        verify(entityService).getEntityFields(ANOTHER_ENTITY_ID);
    }

    @Test
    public void shouldCreateInstanceOfSubclassedEntityWithRelation() {
        mockEntity(SubclassSample.class, ENTITY_ID, entity);
        mockDataService(SubclassSample.class, motechDataService);
        when(motechDataService.retrieve("id", INSTANCE_ID)).thenReturn(new SubclassSample());
        when(entityService.getEntityFields(ENTITY_ID)).thenReturn(asList(
                FieldTestHelper.fieldDto(1L, "superclassInteger", Integer.class.getName(), "Superclass Integer", 7),
                FieldTestHelper.fieldDto(2L, "subclassString", String.class.getName(), "Subclass String", "test"),
                FieldTestHelper.fieldDto(3L, "superclassRelation", TypeDto.ONE_TO_ONE_RELATIONSHIP.getTypeClass(), "Superclass Relationship", null)
        ));

        long relationEntityId = ANOTHER_ENTITY_ID;
        long relationInstanceId = INSTANCE_ID + 1;
        EntityDto relationEntity = mock(EntityDto.class);
        MotechDataService relationDataService = mock(MotechDataService.class);
        mockEntity(TestSample.class, relationEntityId, relationEntity);
        mockDataService(TestSample.class, relationDataService);
        TestSample relatedInstance = new TestSample("test sample", 42);
        when(relationDataService.retrieve("id", relationInstanceId)).thenReturn(relatedInstance);
        when(relationDataService.findById(relationInstanceId)).thenReturn(relatedInstance);

        Map propertiesMap = new HashMap();
        propertiesMap.put("id", relationInstanceId);
        EntityRecord createRecord = new EntityRecord(null, ENTITY_ID, asList(
                FieldTestHelper.fieldRecord("superclassInteger", Integer.class.getName(), "", 77),
                FieldTestHelper.fieldRecord("subclassString", String.class.getName(), "", "test test"),
                FieldTestHelper.fieldRecord(TypeDto.ONE_TO_ONE_RELATIONSHIP, "superclassRelation", "", propertiesMap)
        ));

        ArgumentCaptor<SubclassSample> createCaptor = ArgumentCaptor.forClass(SubclassSample.class);
        instanceService.saveInstance(createRecord);
        verify(motechDataService).create(createCaptor.capture());

        SubclassSample instance = createCaptor.getValue();
        assertEquals(77, (int) instance.getSuperclassInteger());
        assertEquals("test test", instance.getSubclassString());
        assertNotNull(instance.getSuperclassRelation());
        assertEquals(relatedInstance.getStrField(), instance.getSuperclassRelation().getStrField());
        assertEquals(relatedInstance.getIntField(), instance.getSuperclassRelation().getIntField());
    }

    @Test(expected = ObjectUpdateException.class)
    public void shouldThrowExceptionWhileUpdatingReadonlyField() throws ClassNotFoundException {
        mockDataService();
        mockEntity();
        when(motechDataService.retrieve("id", INSTANCE_ID)).thenReturn(new TestSample());

        List<FieldRecord> fieldRecords = asList(
                FieldTestHelper.fieldRecord("strField", String.class.getName(), "", "CannotEditThis")
        );
        fieldRecords.get(0).setNonEditable(true);

        EntityRecord record = new EntityRecord(INSTANCE_ID, ENTITY_ID, fieldRecords);

        instanceService.saveInstance(record);
    }

    @Test
    public void shouldReturnRelatedInstances() {
        mockDataService();
        mockAnotherEntity();
        mockEntity();
        when(serviceForAnotherSample.findById(INSTANCE_ID)).thenReturn(sampleForRelationshipTesting());

        QueryParams queryParams = new QueryParams(1, 2, new Order(Constants.Util.ID_FIELD_NAME, Order.Direction.ASC));
        Records<AnotherSample> records = instanceService.getRelatedFieldValue(ANOTHER_ENTITY_ID, INSTANCE_ID,
                "testClasses", queryParams);

        assertNotNull(records);
        assertEquals(Integer.valueOf(1), records.getPage()); // page 1
        assertEquals(Integer.valueOf(2), records.getTotal()); // 2 pages total
        assertEquals(Integer.valueOf(3), records.getRecords()); // 3 records total
        assertEquals(asList(1L, 2L), extract(records.getRows(), on(TestClass.class).getId()));
    }

    private List buildRelatedRecord() {
        List list = new ArrayList();
        Map recordProperties = new HashMap();
        recordProperties.put("id", 4l);
        list.add(recordProperties);
        recordProperties = new HashMap();
        recordProperties.put("id", 5l);
        list.add(recordProperties);
        return list;
    }

    private void testUpdateCreate(boolean edit) throws ClassNotFoundException {
        final DateTime dtValue = DateUtil.now();

        mockSampleFields();
        mockDataService();
        mockEntity();
        when(motechDataService.retrieve("id", INSTANCE_ID)).thenReturn(new TestSample());

        List<FieldRecord> fieldRecords = asList(
                FieldTestHelper.fieldRecord("strField", String.class.getName(), "", "this is a test"),
                FieldTestHelper.fieldRecord("intField", Integer.class.getName(), "", 16),
                FieldTestHelper.fieldRecord("timeField", Time.class.getName(), "", "10:17"),
                FieldTestHelper.fieldRecord("dtField", DateTime.class.getName(), "", dtValue)
        );

        Long id = (edit) ? INSTANCE_ID : null;
        EntityRecord record = new EntityRecord(id, ENTITY_ID, fieldRecords);

        MDSClassLoader.getInstance().loadClass(TestSample.class.getName());
        instanceService.saveInstance(record);

        ArgumentCaptor<TestSample> captor = ArgumentCaptor.forClass(TestSample.class);
        if (edit) {
            verify(motechDataService).update(captor.capture());
        } else {
            verify(motechDataService).create(captor.capture());
        }

        TestSample sample = captor.getValue();
        assertEquals("this is a test", sample.getStrField());
        assertEquals(Integer.valueOf(16), sample.getIntField());
        assertEquals(new Time(10, 17), sample.getTimeField());
        assertEquals(dtValue, sample.getDtField());
    }

    private void mockDataService() {
        mockDataService(TestSample.class, motechDataService);
    }

    private void mockDataService(Class<?> entityClass, MotechDataService motechDataService) {
        ServiceReference serviceReference = mock(ServiceReference.class);
        when(bundleContext.getServiceReference(ClassName.getInterfaceName(entityClass.getName())))
                .thenReturn(serviceReference);
        when(bundleContext.getService(serviceReference)).thenReturn(motechDataService);
    }

    private void mockSampleFields() {

        when(entityService.getEntityFields(ENTITY_ID)).thenReturn(asList(
                FieldTestHelper.fieldDto(1L, "strField", String.class.getName(), "String field", "Default"),
                FieldTestHelper.fieldDto(2L, "intField", Integer.class.getName(), "Integer field", 7),
                FieldTestHelper.fieldDto(3L, "dtField", DateTime.class.getName(), "DateTime field", null),
                FieldTestHelper.fieldDto(4L, "timeField", Time.class.getName(), "Time field", null),
                // In case of EUDE was created with field name starting from capital letter
                // that capitalized field name will be passed in FieldDto (as LongField in this case).
                // InstanceService should be able to make operations on record regardless of field
                // starts with a capital letter or not.
                FieldTestHelper.fieldDto(5L, "LongField", Long.class.getName(), "Long field", null)
        ));
    }

    private void mockEntity() {
        mockEntity(TestSample.class, ENTITY_ID, entity);
    }

    private void mockEntity(Class<?> entityClass, long entityId, EntityDto entity) {
        when(entityService.getEntity(entityId)).thenReturn(entity);
        when(entity.getClassName()).thenReturn(entityClass.getName());
        when(entity.getId()).thenReturn(entityId);
    }

    private void mockLookups() {
        LookupDto lookup = new LookupDto(TestDataService.LOOKUP_1_NAME, true, true,
                asList(FieldTestHelper.lookupFieldDto(1L, "strField")), true, "singleObject", asList("strField"));
        when(entityService.getLookupByName(ENTITY_ID, TestDataService.LOOKUP_1_NAME)).thenReturn(lookup);
        Map<String, FieldDto> mapping = new HashMap<>();
        mapping.put("strField", FieldTestHelper.fieldDto(1L, "strField", String.class.getName(), "String field", "Default"));
        when(entityService.getLookupFieldsMapping(ENTITY_ID, TestDataService.LOOKUP_1_NAME)).thenReturn(mapping);

        lookup = new LookupDto(TestDataService.LOOKUP_2_NAME, false, true,
                asList(FieldTestHelper.lookupFieldDto(1L, "strField")), false, "multiObject", asList("strField"));
        when(entityService.getLookupByName(ENTITY_ID, TestDataService.LOOKUP_2_NAME)).thenReturn(lookup);
        when(entityService.getLookupFieldsMapping(ENTITY_ID, TestDataService.LOOKUP_2_NAME)).thenReturn(mapping);

        lookup = new LookupDto(TestDataService.NULL_EXPECTING_LOOKUP_NAME, false, true,
                asList(FieldTestHelper.lookupFieldDto(3L, "dtField")), false, "nullParamExpected", asList("dtField"));
        when(entityService.getLookupByName(ENTITY_ID, TestDataService.NULL_EXPECTING_LOOKUP_NAME)).thenReturn(lookup);
        mapping = new HashMap<>();
        mapping.put("dtField", FieldTestHelper.fieldDto(3L, "dtField", DateTime.class.getName(), "DateTime field", null));
        when(entityService.getLookupFieldsMapping(ENTITY_ID, TestDataService.NULL_EXPECTING_LOOKUP_NAME)).thenReturn(mapping);
    }

    private void mockLookupService() {
        mockDataService(TestSample.class, new TestDataService());
    }

    private QueryParams queryParams() {
        return new QueryParams(1, 5, new Order("strField", "desc"));
    }

    private void assertCommonFieldRecordFields(List<FieldRecord> fieldRecords) {
        assertNotNull(fieldRecords);
        assertEquals(5, fieldRecords.size());
        assertEquals(asList("String field", "Integer field", "DateTime field", "Time field", "Long field"),
                extract(fieldRecords, on(FieldRecord.class).getDisplayName()));
        assertEquals(asList("strField", "intField", "dtField", "timeField", "LongField"),
                extract(fieldRecords, on(FieldRecord.class).getName()));
        assertEquals(asList(String.class.getName(), Integer.class.getName(),
                        DateTime.class.getName(), Time.class.getName(), Long.class.getName()),
                extract(fieldRecords, on(FieldRecord.class).getType().getTypeClass()));
    }

    private Collection sampleCollection() {
        return Arrays.asList(new TestSample("a", 1));
    }

    private AnotherSample sampleForRelationshipTesting() {
        Set<TestClass> testClasses = new HashSet<>();
        testClasses.add(new TestClass(1));
        testClasses.add(new TestClass(3));
        testClasses.add(new TestClass(2));

        AnotherSample as = new AnotherSample();
        as.setTestClasses(testClasses);

        return as;
    }

    public static class TestSample {

        private Long id = 4L;

        private String strField = "Default";
        private Integer intField = 7;
        private DateTime dtField;
        private Time timeField;
        private Long longField;

        public TestSample() {
        }

        public TestSample(String strField, Integer intField) {
            this.strField = strField;
            this.intField = intField;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getStrField() {
            return strField;
        }

        public void setStrField(String strField) {
            this.strField = strField;
        }

        public Integer getIntField() {
            return intField;
        }

        public void setIntField(Integer intField) {
            this.intField = intField;
        }

        public DateTime getDtField() {
            return dtField;
        }

        public void setDtField(DateTime dtField) {
            this.dtField = dtField;
        }

        public Time getTimeField() {
            return timeField;
        }

        public void setTimeField(Time timeField) {
            this.timeField = timeField;
        }

        public Long getLongField() {
            return longField;
        }

        public void setLongField(Long longField) {
            this.longField = longField;
        }
    }

    public static class AnotherSample {

        public AnotherSample() {
        }

        AnotherSample(String title, Long id) {
            this.title = title;
            this.id = id;
        }

        private Long id;

        private String title;

        private Set<TestSample> testSamples;

        private TestSample testSample;

        private Set<TestClass> testClasses;

        private TestClass testClass;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Set<TestSample> getTestSamples() {
            return  testSamples;
        }

        public void setTestSamples(Set<TestSample> testSamples) {
            this.testSamples = testSamples;
        }

        public TestSample getTestSample() {
            return testSample;
        }

        public void setTestSample(TestSample testSample) {
            this.testSample = testSample;
        }

        public Set<TestClass> getTestClasses() {
            return testClasses;
        }

        public void setTestClasses(Set<TestClass> testClasses) {
            this.testClasses = testClasses;
        }

        public TestClass getTestClass() {
            return testClass;
        }

        public void setTestClass(TestClass testClass) {
            this.testClass = testClass;
        }
    }

    public static class TestClass {
        private long id;

        public TestClass(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    public static class TestClass__History {
        private long id;
        private long testClass__HistoryCurrentVersion;

        public TestClass__History(long id, long testClass__HistoryCurrentVersion) {
            this.id = id;
            this.testClass__HistoryCurrentVersion = testClass__HistoryCurrentVersion;
        }

        public long getTestClass__HistoryCurrentVersion() {
            return testClass__HistoryCurrentVersion;
        }

        public void setTestClass__HistoryCurrentVersion(long testClass__HistoryCurrentVersion) {
            this.testClass__HistoryCurrentVersion = testClass__HistoryCurrentVersion;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }
    }

    public static class TestDataService extends DefaultMotechDataService<TestSample> {

        public static final String LOOKUP_1_NAME = "Single Object";
        public static final String LOOKUP_2_NAME = "MultiObject";
        public static final String NULL_EXPECTING_LOOKUP_NAME = "nullParamExpected";

        public static final String LOOKUP_1_EXPECTED_PARAM = "strFieldSingle";
        public static final String LOOKUP_2_EXPECTED_PARAM = "strFieldMulti";


        public TestSample singleObject(String strField, QueryParams queryParams) {
            assertEquals(strField, LOOKUP_1_EXPECTED_PARAM);
            assertEquals(Integer.valueOf(1), queryParams.getPage());
            assertEquals(Integer.valueOf(5), queryParams.getPageSize());
            assertEquals("strField descending", queryParams.getOrder().toString());

            return new TestSample("strField", 6);
        }

        public long countSingleObject(String strField) {
            assertEquals(LOOKUP_1_EXPECTED_PARAM, strField);
            return 1;
        }

        public List<TestSample> multiObject(String strField, QueryParams queryParams) {
            assertEquals(LOOKUP_2_EXPECTED_PARAM, strField);
            return asList(new TestSample("one", 1), new TestSample("two", 2));
        }

        public List<TestSample> nullParamExpected(DateTime dtField, QueryParams queryParams) {
            assertNull(dtField);
            return asList(new TestSample("three", 3), new TestSample("four", 4));
        }

        public long countNullParamExpected(DateTime dtField) {
            assertNull(dtField);
            return 2;
        }

        public long countMultiObject(String strField) {
            assertEquals(LOOKUP_2_EXPECTED_PARAM, strField);
            return 22;
        }

        public TestSample findTrashInstanceById(Object instanceId, Object entityId) {
            return new TestSample();
        }

        public void revertFromTrash(Object newInstance, Object trash) {
            assertEquals(newInstance.getClass().getDeclaredMethods(), trash.getClass().getDeclaredMethods());
        }

        @Override
        public Class<TestSample> getClassType() {
            return TestSample.class;
        }
    }

    public static class SuperclassSample {
        private Long id;
        private Integer superclassInteger;
        private TestSample superclassRelation;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getSuperclassInteger() {
            return superclassInteger;
        }

        public void setSuperclassInteger(Integer superclassInteger) {
            this.superclassInteger = superclassInteger;
        }

        public TestSample getSuperclassRelation() {
            return superclassRelation;
        }

        public void setSuperclassRelation(TestSample superclassRelation) {
            this.superclassRelation = superclassRelation;
        }
    }

    public static class SubclassSample extends SuperclassSample {
        private String subclassString;

        public String getSubclassString() {
            return subclassString;
        }

        public void setSubclassString(String subclassString) {
            this.subclassString = subclassString;
        }
    }
}
