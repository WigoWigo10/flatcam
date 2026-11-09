import tempfile
import unittest
from pathlib import Path
from download_community_boards import safe_path, fabrication_file, write_exact


class CommunityDownloadTest(unittest.TestCase):
    def test_rejects_archive_paths_outside_the_corpus(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            for name in ('../outside.gbr','/absolute.gbr','C:/outside.gbr','a\\..\\outside.gbr'):
                with self.assertRaises(ValueError):safe_path(root,name)
            self.assertEqual(root/'fab'/'top.gbr',safe_path(root,'fab/top.gbr'))

    def test_supplier_extensionless_cam_is_kept_but_binary_payload_is_not_extracted(self):
        self.assertTrue(fabrication_file('ok/tl',b'G04 top copper*\n%FSLAX46Y46*%'))
        self.assertTrue(fabrication_file('ok/drl',b'M48\nMETRIC,TZ'))
        self.assertFalse(fabrication_file('ok/payload',b'MZ\0binary'))
        self.assertFalse(fabrication_file('ok/archive.tgz',b'\x1f\x8bcompressed'))

    def test_cached_user_changes_are_never_overwritten(self):
        with tempfile.TemporaryDirectory() as folder:
            target=Path(folder)/'copper.gbr'
            write_exact(target,b'original');write_exact(target,b'original')
            with self.assertRaises(ValueError):write_exact(target,b'changed')
            self.assertEqual(b'original',target.read_bytes())


if __name__=='__main__':unittest.main()
