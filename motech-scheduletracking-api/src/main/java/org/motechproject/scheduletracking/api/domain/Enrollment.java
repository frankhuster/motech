package org.motechproject.scheduletracking.api.domain;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;
import org.ektorp.support.TypeDiscriminator;
import org.joda.time.LocalDate;
import org.motechproject.model.MotechBaseDataObject;
import org.motechproject.model.Time;

import java.util.LinkedList;
import java.util.List;

import static org.motechproject.util.DateUtil.today;

@TypeDiscriminator("doc.type == 'Enrollment'")
public class Enrollment extends MotechBaseDataObject {
    @JsonProperty
    private LocalDate enrollmentDate;
    @JsonProperty
    private String scheduleName;
    @JsonProperty
    private String externalId;
    private String currentMilestoneName;
    @JsonProperty
    private LocalDate referenceDate;
    @JsonProperty
    private Time preferredAlertTime;

    private EnrollmentStatus status;
    private List<MilestoneFulfillment> fulfillments = new LinkedList<MilestoneFulfillment>();

    // For ektorp
    private Enrollment() {
    }

    public Enrollment(String externalId, String scheduleName, String currentMilestoneName, LocalDate referenceDate, LocalDate enrollmentDate, Time preferredAlertTime) {
        this.externalId = externalId;
        this.scheduleName = scheduleName;
        this.currentMilestoneName = currentMilestoneName;
        this.enrollmentDate = enrollmentDate;
        this.referenceDate = referenceDate;
        this.preferredAlertTime = preferredAlertTime;
        this.status = EnrollmentStatus.Active;
    }

    public String getScheduleName() {
        return scheduleName;
    }

    public String getExternalId() {
        return externalId;
    }

    public LocalDate getReferenceDate() {
        return referenceDate;
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate;
    }

    public Time getPreferredAlertTime() {
        return preferredAlertTime;
    }

    public String getCurrentMilestoneName() {
        return currentMilestoneName;
    }

    public List<MilestoneFulfillment> getFulfillments() {
        return fulfillments;
    }

    public LocalDate lastFulfilledDate() {
        if (fulfillments.isEmpty())
            return null;
        return fulfillments.get(fulfillments.size() - 1).getDateFulfilled();
    }

    @JsonIgnore
    public boolean isActive() {
        return status.equals(EnrollmentStatus.Active);
    }

    @JsonIgnore
    public boolean isCompleted() {
        return status.equals(EnrollmentStatus.Completed);
    }

    @JsonIgnore
    public boolean isDefaulted() {
        return status.equals(EnrollmentStatus.Defaulted);
    }

    public void setCurrentMilestoneName(String currentMilestoneName) {
        this.currentMilestoneName = currentMilestoneName;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatus status) {
        this.status = status;
    }

    public Enrollment copyFrom(Enrollment enrollment) {
        enrollmentDate = enrollment.getEnrollmentDate();
        currentMilestoneName = enrollment.getCurrentMilestoneName();
        referenceDate = enrollment.getReferenceDate();
        preferredAlertTime = enrollment.getPreferredAlertTime();
        return this;
    }

    // ektorp methods follow
    private String getType() {
        return type;
    }

    private void setType(String type) {
        this.type = type;
    }

    public LocalDate getCurrentMilestoneStartDate(String firstMilestoneNameFromSchedule, boolean scheduleIsBasedOnAbsoluteWindows) {
        if (currentMilestoneName.equals(firstMilestoneNameFromSchedule) || scheduleIsBasedOnAbsoluteWindows)
            return referenceDate;
        return (fulfillments.isEmpty()) ? enrollmentDate : lastFulfilledDate();
    }

    public void fulfillCurrentMilestone(LocalDate fulfillmentDate) {
        LocalDate dateFulfilled = fulfillmentDate != null ? fulfillmentDate : today();
        fulfillments.add(new MilestoneFulfillment(currentMilestoneName, dateFulfilled));
    }
}