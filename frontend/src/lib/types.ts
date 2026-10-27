export interface Scan {
  id: string;
  sequence: number;
  status: string;
  createdAt: string;
  startedAt?: string;
  finishedAt?: string;
  fileCount: number;
  moduleCount: number;
  analyzedCount: number;
  parseFailureCount: number;
  skippedCount: number;
  findingCount: number;
  reviewCount: number;
  technologyCount: number;
  removedCount: number;
  errorCode?: string;
  errorMessage?: string;
  analyzerVersion: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  sourcePath: string;
  projectType: string;
  jdkVersion?: string;
  buildTool: string;
  sourceFramework?: string;
  targetFramework?: string;
  excludedDirectories: string[];
  excludedFiles: string[];
  analysisScope: string;
  lastScanAt?: string;
  createdAt: string;
  updatedAt: string;
  latestScan?: Scan;
}

export interface ProjectInput {
  name: string;
  description?: string;
  sourcePath: string;
  projectType?: string;
  jdkVersion?: string;
  buildTool?: string;
  sourceFramework?: string;
  targetFramework?: string;
  excludedDirectories?: string[];
  excludedFiles?: string[];
  analysisScope?: string;
}

export interface RuntimeInfo {
  allowedScanRoots: string[];
  maxFileBytes: number;
  maxFiles: number;
  analyzerVersion: string;
  defaultLocale: string;
  readOnlyInventory: boolean;
}

export interface Finding {
  id: string;
  scanId: string;
  fileId?: string;
  filePath: string;
  lineStart?: number;
  lineEnd?: number;
  analysisKind: string;
  objectKey: string;
  summary: string;
  ruleId: string;
  evidence: string;
  certainty: string;
  analyzerVersion: string;
  analyzedAt: string;
  parseStatus: string;
  metadata?: string;
}

export interface AnalysisRelation {
  id: string;
  sourceFileId: string;
  targetFileId: string;
  sourceObjectKey: string;
  targetObjectKey: string;
  relationType: string;
  evidencePath: string;
  lineStart?: number;
  evidence: string;
  ruleId: string;
  certainty: string;
  analyzerVersion: string;
  analyzedAt: string;
}

export interface ProjectFile {
  id: string;
  scanId: string;
  moduleId?: string;
  relativePath: string;
  fileName: string;
  fileType: string;
  sizeBytes: number;
  encoding?: string;
  lastModifiedAt?: string;
  scanStatus: string;
  parseStatus: string;
  parseError?: string;
  frameworkApiCount: number;
  generated: boolean;
  changeType: string;
  structureRole?: string;
}

export interface FileDetail {
  file: ProjectFile;
  findings: Finding[];
  relations: AnalysisRelation[];
}

export interface Technology {
  id: string;
  technologyKey: string;
  name: string;
  category: string;
  status: string;
  apiEvidenceCount: number;
  configurationEvidenceCount: number;
  dependencyEvidenceCount: number;
  totalEvidenceCount: number;
  ruleIds?: string;
  firstEvidencePath?: string;
  firstEvidenceLine?: number;
  analyzerVersion: string;
  analyzedAt: string;
}

export interface FrameworkDetail {
  technology: Technology;
  evidence: Finding[];
}

export interface ModuleInfo {
  id: string;
  relativePath: string;
  name: string;
  sourceRoots: string[];
  testRoots: string[];
  resourceRoots: string[];
  webRoots: string[];
  configRoots: string[];
  sqlRoots: string[];
}

export interface ModuleDependency {
  id: string;
  fromModulePath: string;
  toModulePath: string;
  dependencyType: string;
  evidencePath: string;
  lineStart?: number;
  ruleId: string;
  certainty: string;
  analyzerVersion: string;
  analyzedAt: string;
}

export interface DirectoryNode {
  name: string;
  path: string;
  fileCount: number;
  children: DirectoryNode[];
}

export interface InventoryTree {
  projectId: string;
  scanId?: string;
  modules: ModuleInfo[];
  moduleDependencies: ModuleDependency[];
  directories: DirectoryNode[];
}

export interface Overview {
  projectId: string;
  currentScan?: Scan;
  totalFiles: number;
  analyzedFiles: number;
  parseFailures: number;
  reviewItems: number;
  moduleCount: number;
  fileTypes: Record<string, number>;
  structureRoles: Record<string, number>;
  modules: ModuleInfo[];
  moduleDependencies: ModuleDependency[];
  technologies: Technology[];
  reviewHighlights: Finding[];
  recentScans: Scan[];
  nextAction: string;
  sourceReadOnly: boolean;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ReportDescriptor {
  scanId?: string;
  type: string;
  rowCount: number;
  formats: string[];
}

export interface PathOverride {
  id: string;
  relativePath: string;
  role: string;
}

export interface PathOverrideInput {
  relativePath: string;
  role: string;
}

export interface AuditEvent {
  id: string;
  action: string;
  entityType: string;
  entityId?: string;
  detail?: string;
  occurredAt: string;
}
