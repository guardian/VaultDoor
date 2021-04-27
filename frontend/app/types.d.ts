interface VaultDescription {
  vaultId: string;
  name: string;
}

//see models/FileAttributes.scala
interface FileAttributes {
  fileKey: string;
  name: string;
  parent: string | null | undefined;
  isDir: boolean;
  isRegular: boolean;
  isOther: boolean;
  isSymlink: boolean;
  ctime: string;
  mtime: string;
  atime: string;
  size: number;
}

//see models/PresentableFile.scala
interface GnmMetadata {
  type: string;
  projectId: string | null | undefined;
  commissionId: string | null | undefined;
  projectName: string | null | undefined;
  commissionName: string | null | undefined;
  workingGroupName: string | null | undefined;
  masterId: string | null | undefined;
  masterName: string | null | undefined;
  masterUser: string | null | undefined;
  deliverableBundleId: string | null | undefined;
  deliverableVersion: string | null | undefined;
  deliverableType: string | null | undefined;
}

//see models/PresentableFile.scala
interface FileEntry {
  oid: string;
  metadata: string;
  attributes: FileAttributes;
  gnmMetadata: GnmMetadata | null | undefined;
}

interface NameValuePair {
  name: string;
  value: string;
}

interface PlutoWorkingGroupEntry {
  name: string;
  id: string;
}

interface PlutoWorkingGroupResponse {
  status: string;
  result: PlutoWorkingGroupEntry[];
}

interface PlutoCommissionEntry {
  title: string;
  id: number;
  owner: string;
  productionOffice: string;
  projectCount: number;
  scheduledCompletion: string;
  status: string;
  updated: string;
  workingGroupId: number;
  siteId?: string;
  collectionId?: number;
}

interface PlutoCommissionResponse {
  status: string;
  result: PlutoCommissionEntry[];
}

interface PlutoProjectEntry {
  id: number;
  title: string;
  vidispineId?: string;
}

interface PlutoProjectResponse {
  status: string;
  result: PlutoProjectEntry[];
}

type ValueConverterFunc<T> = (initialData: T) => NameValuePair[];

interface ObjectListResponse<T> {
  status: string;
  entries: T[];
  totalCount?: number;
}
