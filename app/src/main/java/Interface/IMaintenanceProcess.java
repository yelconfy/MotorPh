package Interface;

import Objects.results.SaveResult;
import java.util.List;

/**
 * Generic contract for a reference-data maintenance screen (BKL-26).
 *
 * BKL-35 B-rollout (4th slice, "Reference"): Add/Update now return
 * SaveResult<Void>, same as Delete converted to in B-core. Unlike Delete
 * (which distinguishes SUCCESS/IN_USE/FAILED), Add/Update on these four flat
 * tables have never distinguished failure causes — a failed Insert/Update was
 * always a bare boolean with a generic descriptor-supplied message, and that
 * behavior is preserved exactly: SaveResult.failed() with no message, so
 * ReferenceMaintenancePanel still falls back to descriptor.GetSaveFailedMessage().
 * VALIDATION_FAILED is reachable in principle (a future implementer could add
 * real pre-write validation) but none of the four current implementers emit it.
 */
public interface IMaintenanceProcess<T> {

  /** All rows, in display order. Empty list on read failure. */
  List<T> GetAll();

  /** Insert a new row. */
  SaveResult<Void> Add(T item);

  /** Persist changes to an existing row. */
  SaveResult<Void> Update(T item);

  /**
   * Remove a row. SUCCESS, IN_USE (still referenced elsewhere), or FAILED.
   * Never VALIDATION_FAILED.
   */
  SaveResult<Void> Delete(long id);
}