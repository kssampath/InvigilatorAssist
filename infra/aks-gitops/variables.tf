variable "resource_group_name" {
  description = "Existing resource group to deploy into (the KodeKloud playground creates one per session)."
  type        = string
}

variable "location" {
  description = "Azure region. Defaults to the resource group's region."
  type        = string
  default     = null
}

variable "name_prefix" {
  description = "Short prefix for resource names (lowercase letters and digits)."
  type        = string
  default     = "invig"

  validation {
    condition     = can(regex("^[a-z0-9]{3,10}$", var.name_prefix))
    error_message = "name_prefix must be 3-10 lowercase letters or digits."
  }
}

variable "aks_node_vm_size" {
  description = "AKS node size. KodeKloud allows Standard_D2s_v3, Standard_K8S2_v1 and Standard_K8S_v1."
  type        = string
  default     = "Standard_D2s_v3"
}

variable "aks_node_count" {
  description = "Number of AKS nodes (KodeKloud allows at most 2)."
  type        = number
  default     = 2

  validation {
    condition     = var.aks_node_count >= 1 && var.aks_node_count <= 5
    error_message = "aks_node_count must be between 1 and 5."
  }
}

variable "kubernetes_version" {
  description = "AKS Kubernetes version. null uses the region's default."
  type        = string
  default     = null
}

variable "attach_acr_with_role" {
  description = "Grant AKS the AcrPull role on the registry. Needs role-assignment rights, which the KodeKloud playground does not give; when false the cluster pulls with the acr-pull Secret instead."
  type        = bool
  default     = false
}

variable "jenkins_vm_size" {
  description = "Size of the Jenkins build VM (runs Jenkins plus Docker builds)."
  type        = string
  default     = "Standard_B2s"
}

variable "jenkins_allowed_cidr" {
  description = "Source address range allowed to reach the Jenkins UI (8081). \"Internet\" means anyone (Jenkins still needs its login); set your own IP/32 to restrict it."
  type        = string
  default     = "Internet"
}

variable "ssh_allowed_cidr" {
  description = "Source address range allowed to SSH to the Jenkins VM (key only, no passwords). ci/scripts/configure-jenkins-vm.sh configures Jenkins over SSH, so the machine that runs it must be allowed."
  type        = string
  default     = "Internet"
}

variable "repo_url" {
  description = "Git repository the Jenkins VM clones to get its configuration."
  type        = string
  default     = "https://github.com/kssampath/InvigilatorAssist.git"
}

variable "repo_branch" {
  description = "Branch the Jenkins VM clones."
  type        = string
  default     = "master"
}

variable "tags" {
  description = "Tags added to every resource."
  type        = map(string)

  default = {
    project   = "invigilatorassist"
    managedBy = "terraform"
  }
}
