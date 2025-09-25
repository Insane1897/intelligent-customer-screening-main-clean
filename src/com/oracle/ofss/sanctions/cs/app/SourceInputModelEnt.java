package com.oracle.ofss.sanctions.cs.app;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SourceInputModelEnt {
    private String requestedBy;
    private RequestJson requestJson;

    // Getters and Setters
    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public RequestJson getRequestJson() {
        return requestJson;
    }

    public void setRequestJson(RequestJson requestJson) {
        this.requestJson = requestJson;
    }

public static class RequestJson {
    @JsonProperty("Candidate")
    private List<Candidate> Candidate;

        // Getters and Setters
        public List<Candidate> getCandidate() {
            return Candidate;
        }

        public void setCandidate(List<Candidate> candidate) {
            this.Candidate = candidate;
        }
    }

    public static class Candidate {
@JsonProperty("Source Request ID")
private String sourceRequestID;
@JsonProperty("Applicant ID")
private String applicantID;
@JsonProperty("Candidate Jurisdiction")
private String candidateJurisdiction;
@JsonProperty("Business Domain")
private String businessDomain;
@JsonProperty("Organization Name")
private String organizationName;
@JsonProperty("SSN/TIN")
private String ssnTin;
@JsonProperty("Country Of Incorporation")
private String countryOfIncorporation;
@JsonProperty("Country Of Taxation")
private String countryOfTaxation;
@JsonProperty("Existing Internal ID")
private String existingInternalID;
@JsonProperty("Address")
private List<Address> address;
@JsonProperty("Identification Document")
private List<IdentificationDocument> identificationDocument;
@JsonProperty("Candidate Type")
private String candidateType;
@JsonProperty("searchWithCase")
private boolean searchWithCase;

        // Getters and Setters
        public String getSourceRequestID() {
            return sourceRequestID;
        }

        public void setSourceRequestID(String sourceRequestID) {
            this.sourceRequestID = sourceRequestID;
        }

        public String getApplicantID() {
            return applicantID;
        }

        public void setApplicantID(String applicantID) {
            this.applicantID = applicantID;
        }

        public String getCandidateJurisdiction() {
            return candidateJurisdiction;
        }

        public void setCandidateJurisdiction(String candidateJurisdiction) {
            this.candidateJurisdiction = candidateJurisdiction;
        }

        public String getBusinessDomain() {
            return businessDomain;
        }

        public void setBusinessDomain(String businessDomain) {
            this.businessDomain = businessDomain;
        }

        public String getOrganizationName() {
            return organizationName;
        }

        public void setOrganizationName(String organizationName) {
            this.organizationName = organizationName;
        }

        public String getSsnTin() {
            return ssnTin;
        }

        public void setSsnTin(String ssnTin) {
            this.ssnTin = ssnTin;
        }

        public String getCountryOfIncorporation() {
            return countryOfIncorporation;
        }

        public void setCountryOfIncorporation(String countryOfIncorporation) {
            this.countryOfIncorporation = countryOfIncorporation;
        }

        public String getCountryOfTaxation() {
            return countryOfTaxation;
        }

        public void setCountryOfTaxation(String countryOfTaxation) {
            this.countryOfTaxation = countryOfTaxation;
        }

        public String getExistingInternalID() {
            return existingInternalID;
        }

        public void setExistingInternalID(String existingInternalID) {
            this.existingInternalID = existingInternalID;
        }

        public List<Address> getAddress() {
            return address;
        }

        public void setAddress(List<Address> address) {
            this.address = address;
        }

        public List<IdentificationDocument> getIdentificationDocument() {
            return identificationDocument;
        }

        public void setIdentificationDocument(List<IdentificationDocument> identificationDocument) {
            this.identificationDocument = identificationDocument;
        }

        public String getCandidateType() {
            return candidateType;
        }

        public void setCandidateType(String candidateType) {
            this.candidateType = candidateType;
        }

        public boolean isSearchWithCase() {
            return searchWithCase;
        }

        public void setSearchWithCase(boolean searchWithCase) {
            this.searchWithCase = searchWithCase;
        }
    }

    public static class Address {
@JsonProperty("Street Line 1")
private String streetLine1;
@JsonProperty("City")
private String city;
@JsonProperty("State")
private String state;
@JsonProperty("Country")
private String country;
@JsonProperty("Postal Code")
private String postalCode;

        // Getters and Setters
        public String getStreetLine1() {
            return streetLine1;
        }

        public void setStreetLine1(String streetLine1) {
            this.streetLine1 = streetLine1;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getPostalCode() {
            return postalCode;
        }

        public void setPostalCode(String postalCode) {
            this.postalCode = postalCode;
        }
    }

    public static class IdentificationDocument {
@JsonProperty("Document Type")
private String documentType;
@JsonProperty("Document Number")
private String documentNumber;
@JsonProperty("Issuing Country")
private String issuingCountry;

        // Getters and Setters
        public String getDocumentType() {
            return documentType;
        }

        public void setDocumentType(String documentType) {
            this.documentType = documentType;
        }

        public String getDocumentNumber() {
            return documentNumber;
        }

        public void setDocumentNumber(String documentNumber) {
            this.documentNumber = documentNumber;
        }

        public String getIssuingCountry() {
            return issuingCountry;
        }

        public void setIssuingCountry(String issuingCountry) {
            this.issuingCountry = issuingCountry;
        }
    }
}
